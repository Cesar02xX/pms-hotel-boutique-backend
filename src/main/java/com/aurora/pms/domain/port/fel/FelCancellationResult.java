package com.aurora.pms.domain.port.fel;

/** Provider-neutral result returned after requesting FEL cancellation. */
public record FelCancellationResult(
		String uuid,
		FelStatus status,
		String message) {
}
