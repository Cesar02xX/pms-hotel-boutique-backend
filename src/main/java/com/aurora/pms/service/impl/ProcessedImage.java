package com.aurora.pms.service.impl;

import java.util.Map;

import com.aurora.pms.model.enums.MediaVariant;

/**
 * Resultado de validar y procesar una imagen subida: el formato real del
 * original, sus dimensiones ya orientadas y los bytes de cada variante.
 */
record ProcessedImage(
		String originalContentType,
		String variantContentType,
		int width,
		int height,
		Map<MediaVariant, byte[]> variants
) {
}
