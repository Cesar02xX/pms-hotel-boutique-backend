package com.aurora.pms.model.enums;

/**
 * Versiones optimizadas que se generan al subir una imagen. El número es el
 * lado mayor en píxeles; una imagen más chica no se agranda. El original se
 * guarda aparte y nunca se sirve.
 */
public enum MediaVariant {
	thumb(320),
	medium(960),
	large(1600);

	private final int maxSide;

	MediaVariant(int maxSide) {
		this.maxSide = maxSide;
	}

	public int maxSide() {
		return maxSide;
	}
}
