package com.aurora.pms.service.impl;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Component;

import com.aurora.pms.config.MediaProperties;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.PayloadTooLargeException;
import com.aurora.pms.exception.UnsupportedMediaTypeException;
import com.aurora.pms.model.enums.MediaVariant;
import com.twelvemonkeys.contrib.exif.EXIFUtilities;

/**
 * Valida una imagen por su contenido real (no por la extensión ni por el
 * Content-Type que manda el cliente) y genera las variantes optimizadas.
 *
 * Las variantes se vuelven a codificar desde los píxeles: así se descarta el
 * EXIF (ubicación GPS, datos del teléfono) y se aplica la orientación de la
 * cámara. Salen en JPEG, o en PNG si la imagen tiene transparencia: Java no
 * trae un codificador WebP y uno nativo complicaría Docker y CI.
 */
@Component
public class MediaImageProcessor {

	private static final float JPEG_QUALITY = 0.85f;

	private final MediaProperties mediaProperties;

	public MediaImageProcessor(MediaProperties mediaProperties) {
		this.mediaProperties = mediaProperties;
		// Registra los lectores de TwelveMonkeys (WebP) también dentro del jar de Spring Boot.
		ImageIO.scanForPlugins();
	}

	ProcessedImage process(byte[] content) {
		if (content == null || content.length == 0) {
			throw new BadRequestException("Image file is empty");
		}
		long maxBytes = mediaProperties.getMaxFileSize().toBytes();
		if (content.length > maxBytes) {
			throw new PayloadTooLargeException("Image exceeds the maximum size of "
					+ mediaProperties.getMaxFileSize().toMegabytes() + " MB");
		}

		ImageFormat format = ImageFormat.detect(content);
		if (format == null) {
			throw new UnsupportedMediaTypeException("Only JPEG, PNG and WebP images are allowed");
		}

		validateDimensions(content, format);
		BufferedImage image = decode(content, format);
		boolean keepAlpha = format != ImageFormat.JPEG && image.getColorModel().hasAlpha();

		Map<MediaVariant, byte[]> variants = new EnumMap<>(MediaVariant.class);
		for (MediaVariant variant : MediaVariant.values()) {
			variants.put(variant, encode(resize(image, variant.maxSide(), keepAlpha), keepAlpha));
		}

		return new ProcessedImage(
				format.contentType,
				keepAlpha ? "image/png" : "image/jpeg",
				image.getWidth(),
				image.getHeight(),
				variants
		);
	}

	/** Lee solo la cabecera, antes de decodificar, para no reservar memoria para una imagen gigante. */
	private void validateDimensions(byte[] content, ImageFormat format) {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
			Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format.readerName);
			if (!readers.hasNext() || input == null) {
				throw new UnsupportedMediaTypeException("Image format is not supported by the server");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				long width = reader.getWidth(0);
				long height = reader.getHeight(0);
				if (width <= 0 || height <= 0) {
					throw corrupt();
				}
				if (width * height > mediaProperties.getMaxPixels()) {
					throw new BadRequestException("Image dimensions are too large; maximum is "
							+ mediaProperties.getMaxPixels() + " pixels");
				}
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof BadRequestException || exception instanceof UnsupportedMediaTypeException) {
				throw (RuntimeException) exception;
			}
			throw corrupt();
		}
	}

	private BufferedImage decode(byte[] content, ImageFormat format) {
		try {
			BufferedImage image;
			if (format == ImageFormat.JPEG) {
				IIOImage oriented = EXIFUtilities.readWithOrientation(new ByteArrayInputStream(content));
				image = oriented == null ? null : (BufferedImage) oriented.getRenderedImage();
			} else {
				image = ImageIO.read(new ByteArrayInputStream(content));
			}
			if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
				throw corrupt();
			}
			return image;
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof BadRequestException badRequest) {
				throw badRequest;
			}
			throw corrupt();
		}
	}

	/** Reduce a la mitad mientras se pueda y termina con un último paso: más nítido que un solo salto grande. */
	private BufferedImage resize(BufferedImage source, int maxSide, boolean keepAlpha) {
		double scale = Math.min(1.0, (double) maxSide / Math.max(source.getWidth(), source.getHeight()));
		int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));

		BufferedImage current = source;
		int width = source.getWidth();
		int height = source.getHeight();
		while (width / 2 >= targetWidth && height / 2 >= targetHeight) {
			width /= 2;
			height /= 2;
			current = draw(current, width, height, keepAlpha);
		}
		return draw(current, targetWidth, targetHeight, keepAlpha);
	}

	private BufferedImage draw(BufferedImage source, int width, int height, boolean keepAlpha) {
		BufferedImage target = new BufferedImage(
				width,
				height,
				keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
		);
		Graphics2D graphics = target.createGraphics();
		try {
			if (!keepAlpha) {
				graphics.setColor(Color.WHITE);
				graphics.fillRect(0, 0, width, height);
			}
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(source, 0, 0, width, height, null);
		} finally {
			graphics.dispose();
		}
		return target;
	}

	private byte[] encode(BufferedImage image, boolean png) {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			if (png) {
				ImageIO.write(image, "png", output);
				return output.toByteArray();
			}

			ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
			try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
				ImageWriteParam params = writer.getDefaultWriteParam();
				params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				params.setCompressionQuality(JPEG_QUALITY);
				writer.setOutput(imageOutput);
				writer.write(null, new IIOImage(image, null, null), params);
			} finally {
				writer.dispose();
			}
			return output.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException("Could not encode image variant", exception);
		}
	}

	private static BadRequestException corrupt() {
		return new BadRequestException("Image file is corrupt or unreadable");
	}

	private enum ImageFormat {
		JPEG("image/jpeg", "jpeg"),
		PNG("image/png", "png"),
		WEBP("image/webp", "webp");

		private final String contentType;
		private final String readerName;

		ImageFormat(String contentType, String readerName) {
			this.contentType = contentType;
			this.readerName = readerName;
		}

		/** Firma de los primeros bytes; null si no es uno de los tres formatos admitidos. */
		static ImageFormat detect(byte[] content) {
			if (startsWith(content, 0, 0xFF, 0xD8, 0xFF)) {
				return JPEG;
			}
			if (startsWith(content, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
				return PNG;
			}
			if (startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P')) {
				return WEBP;
			}
			return null;
		}

		private static boolean startsWith(byte[] content, int offset, int... signature) {
			if (content.length < offset + signature.length) {
				return false;
			}
			for (int index = 0; index < signature.length; index++) {
				if ((content[offset + index] & 0xFF) != signature[index]) {
					return false;
				}
			}
			return true;
		}
	}
}
