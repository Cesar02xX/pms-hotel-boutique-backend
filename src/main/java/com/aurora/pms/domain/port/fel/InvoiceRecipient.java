package com.aurora.pms.domain.port.fel;

/** Taxpayer information supplied to the FEL certifier. */
public record InvoiceRecipient(
		String name,
		String taxId,
		String taxIdType,
		String address,
		String email) {
}
