package com.aurora.pms.service.impl;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.response.AuditLogResponse;
import com.aurora.pms.dto.response.ChargeResponse;
import com.aurora.pms.dto.response.DepositResponse;
import com.aurora.pms.dto.response.PaymentResponse;
import com.aurora.pms.dto.response.StayReceiptResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.mapper.GuestFolioMapper;
import com.aurora.pms.mapper.PaymentMapper;
import com.aurora.pms.model.AuditLog;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Deposit;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.ChargeStatus;
import com.aurora.pms.model.enums.DepositStatus;
import com.aurora.pms.model.enums.PaymentStatus;
import com.aurora.pms.repository.AuditLogRepository;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.ChargeRepository;
import com.aurora.pms.repository.DepositRepository;
import com.aurora.pms.repository.OrderRepository;
import com.aurora.pms.repository.PaymentRepository;
import com.aurora.pms.service.GuestAccessService;
import com.aurora.pms.service.ReportingService;

@Service
public class ReportingServiceImpl implements ReportingService {

	private static final ZoneId HOTEL_ZONE = ZoneId.of("America/Guatemala");

	private final BookingRepository bookingRepository;
	private final ChargeRepository chargeRepository;
	private final PaymentRepository paymentRepository;
	private final DepositRepository depositRepository;
	private final OrderRepository orderRepository;
	private final AuditLogRepository auditLogRepository;
	private final GuestAccessService guestAccessService;
	private final GuestFolioMapper guestFolioMapper;
	private final PaymentMapper paymentMapper;

	public ReportingServiceImpl(
			BookingRepository bookingRepository,
			ChargeRepository chargeRepository,
			PaymentRepository paymentRepository,
			DepositRepository depositRepository,
			OrderRepository orderRepository,
			AuditLogRepository auditLogRepository,
			GuestAccessService guestAccessService,
			GuestFolioMapper guestFolioMapper,
			PaymentMapper paymentMapper
	) {
		this.bookingRepository = bookingRepository;
		this.chargeRepository = chargeRepository;
		this.paymentRepository = paymentRepository;
		this.depositRepository = depositRepository;
		this.orderRepository = orderRepository;
		this.auditLogRepository = auditLogRepository;
		this.guestAccessService = guestAccessService;
		this.guestFolioMapper = guestFolioMapper;
		this.paymentMapper = paymentMapper;
	}

	@Override
	@Transactional(readOnly = true)
	public StayReceiptResponse stayReceipt(UUID bookingId) {
		long charges = chargeRepository.sumAmountCentsByBookingIdExcludingStatus(bookingId, ChargeStatus.voided);
		long payments = paymentRepository.sumAmountCentsByBookingIdAndStatus(bookingId, PaymentStatus.completed);
		long appliedDeposits = depositRepository.sumAmountCentsByBookingIdAndStatus(bookingId, DepositStatus.applied);
		List<ChargeResponse> chargeResponses = chargeRepository.findByBookingIdOrderByChargedAtAscCreatedAtAsc(bookingId)
				.stream()
				.map(guestFolioMapper::toChargeResponse)
				.toList();
		List<PaymentResponse> paymentResponses = paymentRepository.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
				.map(paymentMapper::toResponse)
				.toList();
		List<DepositResponse> depositResponses = depositRepository.findByBookingIdOrderByCollectedAtAscCreatedAtAsc(bookingId)
				.stream()
				.map(this::toDepositResponse)
				.toList();
		return new StayReceiptResponse(guestAccessService.getStay(bookingId), chargeResponses, paymentResponses,
				depositResponses, charges - payments - appliedDeposits, "GTQ");
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> operationalReport(LocalDate from, LocalDate to) {
		if (to.isBefore(from)) {
			throw new BadRequestException("to must not be before from");
		}
		OffsetDateTime fromDateTime = from.atStartOfDay(HOTEL_ZONE).toOffsetDateTime();
		OffsetDateTime toExclusiveDateTime = to.plusDays(1).atStartOfDay(HOTEL_ZONE).toOffsetDateTime();
		List<Booking> bookings = bookingRepository.findOverlappingDates(from, to);
		long revenueCents = paymentRepository.sumAmountCentsByStatusAndPaidAtRange(
				PaymentStatus.completed,
				fromDateTime,
				toExclusiveDateTime
		);
		Map<BookingStatus, Long> reservationsByStatus = bookings.stream()
				.collect(Collectors.groupingBy(Booking::getStatus, Collectors.counting()));
		Map<String, Object> report = new LinkedHashMap<>();
		report.put("from", from);
		report.put("to", to);
		report.put("bookings", bookings.size());
		report.put("cancellations", reservationsByStatus.getOrDefault(BookingStatus.cancelled, 0L));
		report.put("revenueCents", revenueCents);
		report.put("occupancyNights", bookings.stream()
				.filter(booking -> booking.getStatus() == BookingStatus.checked_in
						|| booking.getStatus() == BookingStatus.checked_out)
				.mapToLong(booking -> occupiedNightsInRange(booking, from, to))
				.sum());
		report.put("bookingsByStatus", reservationsByStatus);
		report.put("roomServiceOrders", orderRepository.countByRequestedAtRange(fromDateTime, toExclusiveDateTime));
		return report;
	}

	private long occupiedNightsInRange(Booking booking, LocalDate from, LocalDate to) {
		LocalDate start = booking.getCheckIn().isBefore(from) ? from : booking.getCheckIn();
		LocalDate endExclusive = booking.getCheckOut().isAfter(to.plusDays(1)) ? to.plusDays(1) : booking.getCheckOut();
		if (!endExclusive.isAfter(start)) {
			return 0;
		}
		return ChronoUnit.DAYS.between(start, endExclusive);
	}

	@Override
	@Transactional(readOnly = true)
	public List<AuditLogResponse> auditLogs(OffsetDateTime from, OffsetDateTime to) {
		if (to.isBefore(from)) {
			throw new BadRequestException("to must not be before from");
		}
		return auditLogRepository.findByOccurredAtBetweenOrderByOccurredAtDesc(from, to).stream()
				.map(this::toAuditLogResponse)
				.toList();
	}

	private AuditLogResponse toAuditLogResponse(AuditLog log) {
		return new AuditLogResponse(log.getId(), log.getUser() != null ? log.getUser().getId() : null,
				log.getUser() != null ? log.getUser().getEmail() : null, log.getModule(), log.getAction(),
				log.getEntityType(), log.getEntityId(), log.getOccurredAt(), log.getDetails());
	}

	private DepositResponse toDepositResponse(Deposit deposit) {
		return new DepositResponse(deposit.getId(), deposit.getBooking().getId(), deposit.getGuest().getId(),
				deposit.getAmountCents(), deposit.getCurrency(), deposit.getMethod(), deposit.getStatus(),
				deposit.getCollectedAt(), deposit.getRefundedAt(), deposit.getNotes(), deposit.getCreatedAt(),
				deposit.getUpdatedAt());
	}
}
