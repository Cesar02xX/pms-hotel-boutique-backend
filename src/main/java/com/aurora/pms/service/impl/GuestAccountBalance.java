package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.model.GuestAccount;
import com.aurora.pms.model.enums.ChargeStatus;
import com.aurora.pms.model.enums.GuestAccountStatus;
import com.aurora.pms.model.enums.PaymentStatus;
import com.aurora.pms.repository.ChargeRepository;
import com.aurora.pms.repository.GuestAccountRepository;
import com.aurora.pms.repository.PaymentRepository;

/**
 * Única vía para mover el saldo guardado del folio:
 * saldo = cargos no anulados − pagos completados de la reserva.
 * Debe usarse dentro de la transacción de quien registra el movimiento.
 */
@Component
public class GuestAccountBalance {

	private final GuestAccountRepository accountRepository;
	private final ChargeRepository chargeRepository;
	private final PaymentRepository paymentRepository;

	public GuestAccountBalance(
			GuestAccountRepository accountRepository,
			ChargeRepository chargeRepository,
			PaymentRepository paymentRepository
	) {
		this.accountRepository = accountRepository;
		this.chargeRepository = chargeRepository;
		this.paymentRepository = paymentRepository;
	}

	/** Saldo de una reserva calculado desde sus movimientos, para abrir el folio. */
	public long computeFromMovements(UUID bookingId) {
		long charges = chargeRepository.sumAmountCentsByBookingIdExcludingStatus(bookingId, ChargeStatus.voided);
		long payments = completedPaymentsCents(bookingId);
		return subtract(charges, payments);
	}

	public long completedPaymentsCents(UUID bookingId) {
		return paymentRepository.sumAmountCentsByBookingIdAndStatus(bookingId, PaymentStatus.completed);
	}

	/**
	 * Bloquea la cuenta abierta de la reserva para que movimientos concurrentes
	 * no pisen el saldo. Falla si no existe (404) o no está abierta (400).
	 */
	public GuestAccount lockOpenAccount(UUID bookingId) {
		GuestAccount account = accountRepository.findByBookingIdForUpdate(bookingId)
				.orElseThrow(() -> accountNotFound(bookingId));
		ensureOpen(account);
		return account;
	}

	/**
	 * Como {@link #lockOpenAccount}, pero sin folio todavía no hay saldo que
	 * mover: el movimiento se contará al abrirlo.
	 */
	public Optional<GuestAccount> lockOpenAccountIfPresent(UUID bookingId) {
		Optional<GuestAccount> account = accountRepository.findByBookingIdForUpdate(bookingId);
		account.ifPresent(GuestAccountBalance::ensureOpen);
		return account;
	}

	public void apply(GuestAccount account, long deltaCents, OffsetDateTime now) {
		try {
			account.setBalanceCents(Math.addExact(account.getBalanceCents(), deltaCents));
		} catch (ArithmeticException exception) {
			throw new BadRequestException("Guest account balance is too large");
		}
		account.setUpdatedAt(now);
		accountRepository.save(account);
	}

	public static ResourceNotFoundException accountNotFound(UUID bookingId) {
		return new ResourceNotFoundException("Guest account not found for booking: " + bookingId);
	}

	private static void ensureOpen(GuestAccount account) {
		if (account.getStatus() != GuestAccountStatus.open) {
			throw new BadRequestException("Guest account is not open");
		}
	}

	private static long subtract(long charges, long payments) {
		try {
			return Math.subtractExact(charges, payments);
		} catch (ArithmeticException exception) {
			throw new BadRequestException("Guest account balance is too large");
		}
	}
}
