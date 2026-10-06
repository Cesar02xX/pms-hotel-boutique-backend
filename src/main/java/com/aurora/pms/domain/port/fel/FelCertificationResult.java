package com.aurora.pms.domain.port.fel;

/** Provider-neutral result returned after requesting FEL certification. */
public record FelCertificationResult(
		String uuid,
		String series,
		String number,
		String dteUrl,
		String pdfUrl,
		FelStatus status,
		String message) {
}
