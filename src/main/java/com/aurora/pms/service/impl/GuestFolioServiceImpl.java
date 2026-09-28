package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateChargeRequest;
import com.aurora.pms.dto.request.VoidChargeRequest;
import com.aurora.pms.dto.response.ChargeResponse;
import com.aurora.pms.dto.response.GuestFolioResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.GuestFolioMapper;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Charge;
import com.aurora.pms.model.GuestAccount;
import com.aurora.pms.model.Product;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.ChargeStatus;
import com.aurora.pms.model.enums.GuestAccountStatus;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.ChargeRepository;
import com.aurora.pms.repository.GuestAccountRepository;
import com.aurora.pms.repository.ProductRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.service.GuestFolioService;

@Service
public class GuestFolioServiceImpl implements GuestFolioService {

	private static final Set<BookingStatus> STATUSES_WITHOUT_FOLIO =
			EnumSet.of(BookingStatus.cancelled, BookingStatus.no_show);

	private final BookingRepository bookingRepository;
	private final GuestAccountRepository accountRepository;
	private final ChargeRepository chargeRepository;
	private final ProductRepository productRepository;
	private final UserRepository userRepository;
	private final GuestFolioMapper folioMapper;

	public GuestFolioServiceImpl(
			BookingRepository bookingRepository,
			GuestAccountRepository accountRepository,
			ChargeRepository chargeRepository,
			ProductRepository productRepository,
			UserRepository userRepository,
			GuestFolioMapper folioMapper
	) {
		this.bookingRepository = bookingRepository;
		this.accountRepository = accountRepository;
		this.chargeRepository = chargeRepository;
		this.productRepository = productRepository;
		this.userRepository = userRepository;
		this.folioMapper = folioMapper;
	}

	@Override
	@Transactional(readOnly = true)
	public GuestFolioResponse getFolio(UUID bookingId) {
		ensureBookingExists(bookingId);
		GuestAccount account = accountRepository.findByBookingId(bookingId)
				.orElseThrow(() -> accountNotFound(bookingId));
		return toFolioResponse(account);
	}

	@Override
	@Transactional
	public OpenFolioResult openFolio(UUID bookingId) {
		Booking booking = getBooking(bookingId);

		GuestAccount existing = accountRepository.findByBookingId(bookingId).orElse(null);
		if (existing != null) {
			return new OpenFolioResult(toFolioResponse(existing), false);
		}
		if (STATUSES_WITHOUT_FOLIO.contains(booking.getStatus())) {
			throw new BadRequestException("Cannot open a guest account for a booking with status " + booking.getStatus());
		}

		// El saldo inicial parte de los cargos no anulados que ya tuviera la reserva.
		long initialBalance = chargeRepository.sumAmountCentsByBookingIdExcludingStatus(bookingId, ChargeStatus.voided);
		int inserted = accountRepository.insertOpenAccountIfAbsent(
				bookingId, booking.getGuest().getId(), initialBalance, OffsetDateTime.now());

		GuestAccount account = accountRepository.findByBookingId(bookingId)
				.orElseThrow(() -> accountNotFound(bookingId));
		return new OpenFolioResult(toFolioResponse(account), inserted == 1);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ChargeResponse> findCharges(UUID bookingId) {
		ensureBookingExists(bookingId);
		return chargeRepository.findByBookingIdOrderByChargedAtAscCreatedAtAsc(bookingId).stream()
				.map(folioMapper::toChargeResponse)
				.toList();
	}

	@Override
	@Transactional
	public ChargeResponse createCharge(UUID bookingId, CreateChargeRequest request, String actorEmail) {
		Booking booking = getBooking(bookingId);
		GuestAccount account = getOpenAccountForUpdate(bookingId);
		Product product = request.productId() == null ? null : productRepository.findById(request.productId())
				.orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.productId()));

		Charge charge = folioMapper.toChargeEntity(request, booking, product);
		long amountCents = calculateAmount(request.quantity(), request.unitPriceCents());
		OffsetDateTime now = OffsetDateTime.now();
		charge.setAmountCents(amountCents);
		charge.setCurrency(account.getCurrency());
		charge.setStatus(ChargeStatus.posted);
		charge.setChargedAt(now);
		charge.setCreatedAt(now);
		charge.setCreatedByUser(findActor(actorEmail));
		charge = chargeRepository.save(charge);

		account.setBalanceCents(addToBalance(account.getBalanceCents(), amountCents));
		account.setUpdatedAt(now);
		accountRepository.save(account);

		return folioMapper.toChargeResponse(charge);
	}

	@Override
	@Transactional
	public ChargeResponse voidCharge(UUID bookingId, UUID chargeId, VoidChargeRequest request) {
		ensureBookingExists(bookingId);
		GuestAccount account = getOpenAccountForUpdate(bookingId);
		Charge charge = chargeRepository.findByIdAndBookingId(chargeId, bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Charge not found: " + chargeId));

		if (charge.getStatus() == ChargeStatus.voided) {
			throw new BadRequestException("Charge is already voided");
		}

		charge.setStatus(ChargeStatus.voided);
		charge.setVoidReason(request.reason().trim());
		charge = chargeRepository.save(charge);

		account.setBalanceCents(addToBalance(account.getBalanceCents(), -charge.getAmountCents()));
		account.setUpdatedAt(OffsetDateTime.now());
		accountRepository.save(account);

		return folioMapper.toChargeResponse(charge);
	}

	private GuestFolioResponse toFolioResponse(GuestAccount account) {
		List<Charge> charges = chargeRepository.findByBookingIdOrderByChargedAtAscCreatedAtAsc(
				account.getBooking().getId());
		return folioMapper.toFolioResponse(account, charges);
	}

	/** Bloquea la fila de la cuenta para que cargos y anulaciones concurrentes no pisen el saldo. */
	private GuestAccount getOpenAccountForUpdate(UUID bookingId) {
		GuestAccount account = accountRepository.findByBookingIdForUpdate(bookingId)
				.orElseThrow(() -> accountNotFound(bookingId));
		if (account.getStatus() != GuestAccountStatus.open) {
			throw new BadRequestException("Guest account is not open");
		}
		return account;
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

	private Booking getBooking(UUID bookingId) {
		return bookingRepository.findById(bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
	}

	private static ResourceNotFoundException accountNotFound(UUID bookingId) {
		return new ResourceNotFoundException("Guest account not found for booking: " + bookingId);
	}

	private static long calculateAmount(int quantity, long unitPriceCents) {
		try {
			return Math.multiplyExact(quantity, unitPriceCents);
		} catch (ArithmeticException exception) {
			throw new BadRequestException("Charge amount is too large");
		}
	}

	private static long addToBalance(long balanceCents, long deltaCents) {
		try {
			return Math.addExact(balanceCents, deltaCents);
		} catch (ArithmeticException exception) {
			throw new BadRequestException("Guest account balance is too large");
		}
	}
}
