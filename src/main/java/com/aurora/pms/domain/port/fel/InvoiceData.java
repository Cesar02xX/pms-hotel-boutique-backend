package com.aurora.pms.domain.port.fel;

import java.util.List;
import java.util.Map;

/** Provider-neutral data required to request FEL invoice certification. */
public record InvoiceData(
		String invoiceReference,
		String currency,
		long totalAmountCents,
		InvoiceRecipient recipient,
		List<InvoiceLine> lines,
		Map<String, String> metadata) {

	public InvoiceData {
		lines = lines == null ? List.of() : List.copyOf(lines);
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}
}
