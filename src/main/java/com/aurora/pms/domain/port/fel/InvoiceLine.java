package com.aurora.pms.domain.port.fel;

/** A provider-neutral line item in an invoice. */
public record InvoiceLine(
		String description,
		long quantity,
		long unitAmountCents,
		long totalAmountCents) {
}
