package com.aurora.pms.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateDepositRequest;
import com.aurora.pms.dto.request.RefundDepositRequest;
import com.aurora.pms.dto.response.DepositResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.DepositMapper;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Deposit;
import com.aurora.pms.model.GuestAccount;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.DepositStatus;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.DepositRepository;
import com.aurora.pms.service.DepositService;

@Service
public class DepositServiceImpl implements DepositService {

	private final BookingRepository bookingRepository;
	private final DepositRepository depositRepository;
	private final DepositMapper depositMapper;
	private final GuestAccountBalance balance;
	private final Clock clock;

	public DepositServiceImpl(
			BookingRepository bookingRepository,
			DepositRepository depositRepository,
			DepositMapper depositMapper,
			GuestAccountBalance balance,
			Clock clock
	) {
		this.bookingRepository = bookingRepository;
		this.depositRepository = depositRepository;
		this.depositMapper = depositMapper;
		this.balance = balance;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<DepositResponse> findAllByBookingId(UUID bookingId) {
		ensureBookingExists(bookingId);
		return depositRepository.findByBookingIdOrderByCollectedAtAscCreatedAtAsc(bookingId).stream()
				.map(depositMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional
	public DepositResponse create(UUID bookingId, CreateDepositRequest request) {
		Booking booking = getBookingForFinancialMutation(bookingId);
		ensureFinancialMovementsAllowed(booking);

		Deposit deposit = depositMapper.toEntity(request, booking);
		OffsetDateTime now = OffsetDateTime.now(clock);
		deposit.setCurrency(booking.getCurrency());
		deposit.setStatus(DepositStatus.held);
		deposit.setCollectedAt(now);
		deposit.setCreatedAt(now);
		deposit.setUpdatedAt(now);

		return depositMapper.toResponse(depositRepository.save(deposit));
	}

	@Override
	@Transactional
	public DepositResponse apply(UUID bookingId, UUID depositId) {
		Booking booking = getBookingForFinancialMutation(bookingId);
		ensureFinancialMovementsAllowed(booking);
		Deposit deposit = depositRepository.findByIdAndBookingIdForUpdate(depositId, bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Deposit not found: " + depositId));

		if (deposit.getStatus() == DepositStatus.applied) {
			return depositMapper.toResponse(deposit);
		}
		if (deposit.getStatus() != DepositStatus.held) {
			throw new BadRequestException("Only held deposits can be applied");
		}

		OffsetDateTime now = OffsetDateTime.now(clock);
		GuestAccount account = balance.lockOpenAccount(bookingId);
		deposit.setStatus(DepositStatus.applied);
		deposit.setUpdatedAt(now);
		deposit.setNotes(appendApplyNote(deposit.getNotes()));
		deposit = depositRepository.save(deposit);

		balance.apply(account, -deposit.getAmountCents(), now);
		return depositMapper.toResponse(deposit);
	}

	@Override
	@Transactional
	public DepositResponse refund(UUID bookingId, UUID depositId, RefundDepositRequest request) {
		ensureBookingExists(bookingId);
		// Bloquea el depósito para que dos reembolsos simultáneos no se apliquen ambos.
		Deposit deposit = depositRepository.findByIdAndBookingIdForUpdate(depositId, bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Deposit not found: " + depositId));

		if (deposit.getStatus() == DepositStatus.refunded) {
			throw new BadRequestException("Deposit is already refunded");
		}
		if (deposit.getStatus() != DepositStatus.held) {
			throw new BadRequestException("Only held deposits can be refunded");
		}

		OffsetDateTime now = OffsetDateTime.now(clock);
		deposit.setStatus(DepositStatus.refunded);
		deposit.setRefundedAt(now);
		deposit.setUpdatedAt(now);
		deposit.setNotes(appendRefundReason(deposit.getNotes(), request == null ? null : request.reason()));

		return depositMapper.toResponse(depositRepository.save(deposit));
	}

	private static String appendRefundReason(String notes, String reason) {
		if (reason == null || reason.isBlank()) {
			return notes;
		}
		String refundNote = "Refund: " + reason.trim();
		return notes == null ? refundNote : notes + "\n" + refundNote;
	}

	private void ensureBookingExists(UUID bookingId) {
		if (!bookingRepository.existsById(bookingId)) {
			throw new ResourceNotFoundException("Booking not found: " + bookingId);
		}
	}

	private Booking getBooking(UUID bookingId) {
		return bookingRepository.findById(bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
	}

	private Booking getBookingForFinancialMutation(UUID bookingId) {
		return bookingRepository.findByIdForUpdate(bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
	}

	private static void ensureFinancialMovementsAllowed(Booking booking) {
		if (booking.getStatus() == BookingStatus.cancelled
				|| booking.getStatus() == BookingStatus.no_show
				|| booking.getStatus() == BookingStatus.checked_out) {
			throw new BadRequestException(
					"Cannot create financial movements for a booking with status " + booking.getStatus());
		}
	}

	private static String appendApplyNote(String notes) {
		String applyNote = "Applied to folio";
		return notes == null ? applyNote : notes + "\n" + applyNote;
	}
}
