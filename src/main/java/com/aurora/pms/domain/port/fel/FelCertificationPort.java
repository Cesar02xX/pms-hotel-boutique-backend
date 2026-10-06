package com.aurora.pms.domain.port.fel;

/** Port for a Guatemala FEL/DTE certifier. */
public interface FelCertificationPort {

	FelCertificationResult emitInvoice(InvoiceData data);

	FelCancellationResult cancelInvoice(String uuid, String reason);
}
