package com.aurora.pms.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

import javax.imageio.ImageIO;

/** Imágenes reales generadas en memoria para las pruebas de carga. */
public final class TestImages {

	/** WebP lossless de 1×1 píxel; Java no trae codificador WebP para generarlo. */
	private static final String WEBP_1X1 = "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==";

	private TestImages() {
	}

	public static byte[] jpeg(int width, int height) {
		return encode(opaque(width, height), "jpeg");
	}

	public static byte[] png(int width, int height) {
		return encode(opaque(width, height), "png");
	}

	public static byte[] transparentPng(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(new Color(200, 30, 30, 120));
		graphics.fillRect(0, 0, width / 2, height);
		graphics.dispose();
		return encode(image, "png");
	}

	public static byte[] webp() {
		return Base64.getDecoder().decode(WEBP_1X1);
	}

	/** Firma de JPEG válida seguida de basura: pasa la detección de formato pero no se puede leer. */
	public static byte[] corruptJpeg() {
		byte[] content = new byte[64];
		content[0] = (byte) 0xFF;
		content[1] = (byte) 0xD8;
		content[2] = (byte) 0xFF;
		for (int index = 3; index < content.length; index++) {
			content[index] = (byte) index;
		}
		return content;
	}

	private static BufferedImage opaque(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(new Color(40, 90, 160));
		graphics.fillRect(0, 0, width, height);
		graphics.setColor(Color.ORANGE);
		graphics.fillOval(0, 0, width / 2, height / 2);
		graphics.dispose();
		return image;
	}

	private static byte[] encode(BufferedImage image, String format) {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(image, format, output);
			return output.toByteArray();
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}
}
