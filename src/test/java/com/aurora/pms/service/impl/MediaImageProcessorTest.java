package com.aurora.pms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.aurora.pms.config.MediaProperties;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.PayloadTooLargeException;
import com.aurora.pms.exception.UnsupportedMediaTypeException;
import com.aurora.pms.model.enums.MediaVariant;
import com.aurora.pms.support.TestImages;

class MediaImageProcessorTest {

	private MediaProperties properties;
	private MediaImageProcessor processor;

	@BeforeEach
	void setUp() {
		properties = new MediaProperties();
		processor = new MediaImageProcessor(properties);
	}

	@Test
	void jpegProducesThreeJpegVariantsScaledByLongestSide() throws IOException {
		ProcessedImage processed = processor.process(TestImages.jpeg(2000, 1000));

		assertThat(processed.originalContentType()).isEqualTo("image/jpeg");
		assertThat(processed.variantContentType()).isEqualTo("image/jpeg");
		assertThat(processed.width()).isEqualTo(2000);
		assertThat(processed.height()).isEqualTo(1000);
		assertDimensions(processed, MediaVariant.thumb, 320, 160);
		assertDimensions(processed, MediaVariant.medium, 960, 480);
		assertDimensions(processed, MediaVariant.large, 1600, 800);
	}

	@Test
	void smallImageIsNotUpscaled() throws IOException {
		ProcessedImage processed = processor.process(TestImages.png(400, 300));

		assertThat(processed.originalContentType()).isEqualTo("image/png");
		assertThat(processed.variantContentType()).isEqualTo("image/jpeg");
		assertDimensions(processed, MediaVariant.thumb, 320, 240);
		assertDimensions(processed, MediaVariant.medium, 400, 300);
		assertDimensions(processed, MediaVariant.large, 400, 300);
	}

	@Test
	void transparentPngKeepsTransparencyAsPng() throws IOException {
		ProcessedImage processed = processor.process(TestImages.transparentPng(500, 500));

		assertThat(processed.variantContentType()).isEqualTo("image/png");
		BufferedImage thumb = read(processed.variants().get(MediaVariant.thumb));
		assertThat(thumb.getColorModel().hasAlpha()).isTrue();
	}

	@Test
	void webpIsAcceptedAndReencodedAsJpeg() throws IOException {
		ProcessedImage processed = processor.process(TestImages.webp());

		assertThat(processed.originalContentType()).isEqualTo("image/webp");
		assertThat(processed.variants()).containsOnlyKeys(MediaVariant.values());
		assertThat(read(processed.variants().get(MediaVariant.large))).isNotNull();
	}

	@Test
	void rejectsFilesThatAreNotImagesRegardlessOfName() {
		byte[] text = "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> processor.process(text))
				.isInstanceOf(UnsupportedMediaTypeException.class);
	}

	@Test
	void rejectsEmptyFile() {
		assertThatThrownBy(() -> processor.process(new byte[0]))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("empty");
	}

	@Test
	void rejectsCorruptImage() {
		assertThatThrownBy(() -> processor.process(TestImages.corruptJpeg()))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("corrupt");
	}

	@Test
	void rejectsFileOverConfiguredSize() {
		properties.setMaxFileSize(DataSize.ofBytes(100));

		assertThatThrownBy(() -> processor.process(TestImages.png(200, 200)))
				.isInstanceOf(PayloadTooLargeException.class);
	}

	@Test
	void rejectsDimensionsOverPixelLimitBeforeDecoding() {
		properties.setMaxPixels(10_000);

		assertThatThrownBy(() -> processor.process(TestImages.png(200, 200)))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("dimensions");
	}

	private static void assertDimensions(ProcessedImage processed, MediaVariant variant, int width, int height)
			throws IOException {
		BufferedImage image = read(processed.variants().get(variant));
		assertThat(image.getWidth()).as(variant + " width").isEqualTo(width);
		assertThat(image.getHeight()).as(variant + " height").isEqualTo(height);
	}

	private static BufferedImage read(byte[] content) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(content));
	}
}
