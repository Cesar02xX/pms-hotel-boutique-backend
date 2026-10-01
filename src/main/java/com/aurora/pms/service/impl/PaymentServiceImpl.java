package com.aurora.pms.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreatePaymentRequest;
import com.aurora.pms.dto.response.PaymentResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.PaymentMapper;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.GuestAccount;
import com.aurora.pms.model.Payment;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.PaymentStatus;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.PaymentRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.service.PaymentService;

@Service
public class PaymentServiceImpl implements PaymentService {

	private final BookingRepository bookingRepository;
	private final PaymentRepository paymentRepository;
	private final UserRepository userRepository;
	private final PaymentMapper paymentMapper;
	private final GuestAccountBalance balance;
	private final Clock clock;

	public PaymentServiceImpl(
			BookingRepository bookingRepository,
			PaymentRepository paymentRepository,
			UserRepository userRepository,
			PaymentMapper paymentMapper,
			GuestAccountBalance balance,
			Clock clock
	) {
		this.bookingRepository = bookingRepository;
		this.paymentRepository = paymentRepository;
		this.userRepository = userRepository;
		this.paymentMapper = paymentMapper;
		this.balance = balance;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PaymentResponse> findAllByBookingId(UUID bookingId) {
		ensureBookingExists(bookingId);
		return paymentRepository.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
				.map(paymentMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional
	public PaymentResponse create(UUID bookingId, CreatePaymentRequest request, String actorEmail) {
		Booking booking = getBookingForFolioMutation(bookingId);
		ensureFinancialMovementsAllowed(booking);
		GuestAccount account = balance.lockOpenAccount(bookingId);
		ensureNoOverpayment(account, request.amountCents());

		// Sin pasarela real: el pago se registra ya cobrado en recepción.
		Payment payment = paymentMapper.toEntity(request, booking);
		OffsetDateTime now = OffsetDateTime.now(clock);
		payment.setCurrency(booking.getCurrency());
		payment.setStatus(PaymentStatus.completed);
		payment.setPaidAt(now);
		payment.setCreatedAt(now);
		payment.setProcessedByUser(findActor(actorEmail));
		payment = paymentRepository.save(payment);

		long amountCents = payment.getAmountCents();
		balance.apply(account, -amountCents, now);

		return paymentMapper.toResponse(payment);
	}

	private User findActor(String actorEmail) {
		if (actorEmail == null) {
			return null;
		}
		return userRepository.findByEmail(actorEmail).orElse(null);
	}

	private void ensureBookingExists(UUID bookingId) {
		if (!bookingRepository.existsById(bookingId)) {
			throw new ResourceNotFoundException("Booking not found: " + bookingId);
		}
	}

	private Booking getBookingForFolioMutation(UUID bookingId) {
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

	private static void ensureNoOverpayment(GuestAccount account, long amountCents) {
		if (amountCents > account.getBalanceCents()) {
			throw new ConflictException("Payment amount exceeds guest account balance");
		}
	}
}
