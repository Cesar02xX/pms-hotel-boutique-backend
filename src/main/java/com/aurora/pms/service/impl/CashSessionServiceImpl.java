package com.aurora.pms.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CloseCashSessionRequest;
import com.aurora.pms.dto.request.CreateCashMovementRequest;
import com.aurora.pms.dto.request.OpenCashSessionRequest;
import com.aurora.pms.dto.response.CashMovementResponse;
import com.aurora.pms.dto.response.CashSessionResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.CashSessionMapper;
import com.aurora.pms.model.CashMovement;
import com.aurora.pms.model.CashSession;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.CashMovementType;
import com.aurora.pms.model.enums.CashSessionStatus;
import com.aurora.pms.repository.CashMovementRepository;
import com.aurora.pms.repository.CashSessionRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.service.CashSessionService;

/**
 * Caja única del hotel: el modelo no distingue cajas ni terminales, así que
 * solo puede haber una sesión abierta a la vez, y puede cerrarla otro usuario.
 */
@Service
public class CashSessionServiceImpl implements CashSessionService {

	private final CashSessionRepository cashSessionRepository;
	private final CashMovementRepository cashMovementRepository;
	private final UserRepository userRepository;
	private final CashSessionMapper cashSessionMapper;
	private final Clock clock;

	public CashSessionServiceImpl(
			CashSessionRepository cashSessionRepository,
			CashMovementRepository cashMovementRepository,
			UserRepository userRepository,
			CashSessionMapper cashSessionMapper,
			Clock clock
	) {
		this.cashSessionRepository = cashSessionRepository;
		this.cashMovementRepository = cashMovementRepository;
		this.userRepository = userRepository;
		this.cashSessionMapper = cashSessionMapper;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public CashSessionResponse findCurrent(String actorEmail) {
		User actor = requireActor(actorEmail);
		CashSession session = cashSessionRepository
				.findFirstByOpenedByUserEmailAndStatusOrderByOpenedAtDesc(actor.getEmail(), CashSessionStatus.open)
				.orElseThrow(() -> new ResourceNotFoundException("No open cash session for current user"));
		return toResponse(session);
	}

	@Override
	@Transactional
	public CashSessionResponse open(OpenCashSessionRequest request, String actorEmail) {
		User actor = requireActor(actorEmail);

		cashSessionRepository.lockOpening();
		if (cashSessionRepository.existsByOpenedByUserIdAndStatus(actor.getId(), CashSessionStatus.open)) {
			throw new BadRequestException("There is already an open cash session for current user");
		}

		OffsetDateTime now = OffsetDateTime.now(clock);
		CashSession session = new CashSession();
		session.setOpenedByUser(actor);
		session.setOpenedAt(now);
		session.setOpeningBalanceCents(request.openingBalanceCents());
		session.setStatus(CashSessionStatus.open);
		session.setNotes(trimToNull(request.notes()));
		session.setCreatedAt(now);
		session.setUpdatedAt(now);
		session = cashSessionRepository.save(session);

		return cashSessionMapper.toResponse(session, 0L, 0L, session.getOpeningBalanceCents());
	}

	@Override
	@Transactional
	public CashSessionResponse close(UUID sessionId, CloseCashSessionRequest request, String actorEmail) {
		CashSession session = getOpenSessionForUpdate(sessionId, "Cash session is already closed");

		long income = cashMovementRepository.sumAmountCents(sessionId, CashMovementType.income);
		long expense = cashMovementRepository.sumAmountCents(sessionId, CashMovementType.expense);
		long expected = session.getOpeningBalanceCents() + income - expense;
		long counted = request.countedBalanceCents();

		OffsetDateTime now = OffsetDateTime.now(clock);
		session.setStatus(CashSessionStatus.closed);
		session.setClosedByUser(findActor(actorEmail));
		session.setClosedAt(now);
		session.setExpectedBalanceCents(expected);
		session.setCountedBalanceCents(counted);
		session.setDifferenceCents(counted - expected);
		session.setNotes(appendNotes(session.getNotes(), trimToNull(request.notes())));
		session.setUpdatedAt(now);
		session = cashSessionRepository.save(session);

		return cashSessionMapper.toResponse(session, income, expense, expected);
	}

	@Override
	@Transactional(readOnly = true)
	public List<CashMovementResponse> findMovements(UUID sessionId) {
		if (!cashSessionRepository.existsById(sessionId)) {
			throw new ResourceNotFoundException("Cash session not found: " + sessionId);
		}
		return cashMovementRepository.findByCashSessionIdOrderByOccurredAtAscCreatedAtAsc(sessionId).stream()
				.map(cashSessionMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional
	public CashMovementResponse createMovement(
			UUID sessionId,
			CreateCashMovementRequest request,
			String actorEmail
	) {
		// El bloqueo evita que la sesión se cierre mientras se registra el movimiento.
		CashSession session = getOpenSessionForUpdate(
				sessionId, "Cannot register movements on a closed cash session");

		if (request.type() == CashMovementType.expense) {
			long available = currentBalance(session);
			if (request.amountCents() > available) {
				throw new BadRequestException(
						"Expense exceeds the cash available in the session (" + available + " cents)");
			}
		}

		// Movimiento manual: no se vincula a Payments en este módulo.
		CashMovement movement = cashSessionMapper.toEntity(request, session);
		OffsetDateTime now = OffsetDateTime.now(clock);
		movement.setCurrency(session.getCurrency());
		movement.setResponsibleUser(findActor(actorEmail));
		movement.setOccurredAt(now);
		movement.setCreatedAt(now);
		movement = cashMovementRepository.save(movement);

		return cashSessionMapper.toResponse(movement);
	}

	private CashSessionResponse toResponse(CashSession session) {
		long income = cashMovementRepository.sumAmountCents(session.getId(), CashMovementType.income);
		long expense = cashMovementRepository.sumAmountCents(session.getId(), CashMovementType.expense);
		long expected = session.getStatus() == CashSessionStatus.closed && session.getExpectedBalanceCents() != null
				? session.getExpectedBalanceCents()
				: session.getOpeningBalanceCents() + income - expense;
		return cashSessionMapper.toResponse(session, income, expense, expected);
	}

	private long currentBalance(CashSession session) {
		UUID sessionId = session.getId();
		return session.getOpeningBalanceCents()
				+ cashMovementRepository.sumAmountCents(sessionId, CashMovementType.income)
				- cashMovementRepository.sumAmountCents(sessionId, CashMovementType.expense);
	}

	private CashSession getOpenSessionForUpdate(UUID sessionId, String closedMessage) {
		CashSession session = cashSessionRepository.findByIdForUpdate(sessionId)
				.orElseThrow(() -> new ResourceNotFoundException("Cash session not found: " + sessionId));
		if (session.getStatus() != CashSessionStatus.open) {
			throw new BadRequestException(closedMessage);
		}
		return session;
	}

	private User findActor(String actorEmail) {
		if (actorEmail == null) {
			return null;
		}
		return userRepository.findByEmail(actorEmail).orElse(null);
	}

	private User requireActor(String actorEmail) {
		User actor = findActor(actorEmail);
		if (actor == null) {
			throw new InsufficientAuthenticationException("Authenticated user not found");
		}
		return actor;
	}

	private static String appendNotes(String current, String addition) {
		if (addition == null) {
			return current;
		}
		return current == null ? addition : current + "\n" + addition;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
