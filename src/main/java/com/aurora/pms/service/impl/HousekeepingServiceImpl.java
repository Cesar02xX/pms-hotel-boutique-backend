package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.response.StayoverCleaningResponse;
import com.aurora.pms.dto.response.HousekeepingRoomResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.HousekeepingRoomMapper;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.ServiceRequest;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.RoomRepository;
import com.aurora.pms.repository.ServiceRequestRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.service.HousekeepingService;

@Service
public class HousekeepingServiceImpl implements HousekeepingService {

	private final RoomRepository roomRepository;
	private final BookingRepository bookingRepository;
	private final ServiceRequestRepository serviceRequestRepository;
	private final UserRepository userRepository;
	private final HousekeepingRoomMapper housekeepingRoomMapper;

	public HousekeepingServiceImpl(
			RoomRepository roomRepository,
			BookingRepository bookingRepository,
			ServiceRequestRepository serviceRequestRepository,
			UserRepository userRepository,
			HousekeepingRoomMapper housekeepingRoomMapper
	) {
		this.roomRepository = roomRepository;
		this.bookingRepository = bookingRepository;
		this.serviceRequestRepository = serviceRequestRepository;
		this.userRepository = userRepository;
		this.housekeepingRoomMapper = housekeepingRoomMapper;
	}

	@Override
	@Transactional(readOnly = true)
	public List<HousekeepingRoomResponse> findAll(RoomHousekeepingStatus housekeepingStatus) {
		List<Room> rooms = housekeepingStatus == null
				? roomRepository.findAllByOrderByRoomNumber()
				: roomRepository.findByHousekeepingStatusOrderByRoomNumber(housekeepingStatus);

		return rooms.stream()
				.map(housekeepingRoomMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public HousekeepingRoomResponse findById(UUID roomId) {
		return housekeepingRoomMapper.toResponse(getRoom(roomId));
	}

	@Override
	@Transactional
	public HousekeepingRoomResponse startCleaning(UUID roomId, String actorEmail) {
		return transition(
				roomId,
				RoomHousekeepingStatus.dirty,
				RoomHousekeepingStatus.cleaning,
				"start cleaning",
				findActor(actorEmail)
		);
	}

	@Override
	@Transactional
	public HousekeepingRoomResponse completeCleaning(UUID roomId, String actorEmail) {
		return transition(
				roomId,
				RoomHousekeepingStatus.cleaning,
				RoomHousekeepingStatus.clean,
				"complete cleaning",
				findActor(actorEmail)
		);
	}

	@Override
	@Transactional
	public HousekeepingRoomResponse inspect(UUID roomId, String actorEmail) {
		return transition(
				roomId,
				RoomHousekeepingStatus.clean,
				RoomHousekeepingStatus.inspected,
				"inspect",
				findActor(actorEmail)
		);
	}

	@Override
	@Transactional(readOnly = true)
	public List<StayoverCleaningResponse> findStayoverCleanings(UUID bookingId) {
		if (!bookingRepository.existsById(bookingId)) {
			throw new ResourceNotFoundException("Booking not found: " + bookingId);
		}
		return serviceRequestRepository
				.findByTypeAndBookingIdOrderByRequestedAtAscCreatedAtAsc(ServiceRequestType.housekeeping, bookingId)
				.stream()
				.map(this::toStayoverResponse)
				.toList();
	}

	@Override
	@Transactional
	public StayoverCleaningResponse createStayoverCleaning(
			UUID roomId,
			UUID bookingId,
			String description,
			String actorEmail
	) {
		User actor = findActor(actorEmail);
		Booking booking = bookingRepository.findByIdForUpdate(bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
		Room room = getRoomForUpdate(roomId);
		ensureStayoverBooking(room, booking);

		OffsetDateTime now = OffsetDateTime.now();
		ServiceRequest request = new ServiceRequest();
		request.setBooking(booking);
		request.setRoom(room);
		request.setGuest(booking.getGuest());
		request.setType(ServiceRequestType.housekeeping);
		request.setStatus(ServiceRequestStatus.pending);
		request.setDescription(trimToDefault(description, "Stayover cleaning"));
		request.setResponsibleUser(actor);
		request.setRequestedAt(now);
		request.setCreatedAt(now);
		request.setUpdatedAt(now);
		return toStayoverResponse(serviceRequestRepository.save(request));
	}

	@Override
	@Transactional
	public StayoverCleaningResponse startStayoverCleaning(UUID requestId, String actorEmail) {
		ServiceRequest request = getStayoverForUpdate(requestId);
		if (request.getStatus() != ServiceRequestStatus.pending) {
			throw new BadRequestException("Cannot start stayover cleaning from status: " + request.getStatus());
		}
		OffsetDateTime now = OffsetDateTime.now();
		request.setStatus(ServiceRequestStatus.in_progress);
		request.setResponsibleUser(findActor(actorEmail));
		request.setStartedAt(now);
		request.setUpdatedAt(now);
		return toStayoverResponse(serviceRequestRepository.save(request));
	}

	@Override
	@Transactional
	public StayoverCleaningResponse completeStayoverCleaning(UUID requestId, String actorEmail) {
		ServiceRequest request = getStayoverForUpdate(requestId);
		if (request.getStatus() != ServiceRequestStatus.in_progress) {
			throw new BadRequestException("Cannot complete stayover cleaning from status: " + request.getStatus());
		}
		OffsetDateTime now = OffsetDateTime.now();
		request.setStatus(ServiceRequestStatus.completed);
		request.setResponsibleUser(findActor(actorEmail));
		request.setCompletedAt(now);
		request.setUpdatedAt(now);
		return toStayoverResponse(serviceRequestRepository.save(request));
	}

	private HousekeepingRoomResponse transition(
			UUID roomId,
			RoomHousekeepingStatus expected,
			RoomHousekeepingStatus next,
			String action,
			User actor
	) {
		Room room = getRoomForUpdate(roomId);
		if (room.getHousekeepingStatus() != expected) {
			throw new BadRequestException(
					"Cannot " + action + " room from housekeeping status: " + room.getHousekeepingStatus()
			);
		}

		OffsetDateTime now = OffsetDateTime.now();
		room.setHousekeepingStatus(next);
		if (next == RoomHousekeepingStatus.cleaning) {
			room.setCleaningUser(actor);
			room.setCleaningStartedAt(now);
			room.setCleaningCompletedAt(null);
			room.setInspectorUser(null);
			room.setInspectedAt(null);
		}
		if (next == RoomHousekeepingStatus.clean) {
			room.setCleaningUser(actor);
			room.setCleaningCompletedAt(now);
			room.setInspectorUser(null);
			room.setInspectedAt(null);
		}
		if (next == RoomHousekeepingStatus.inspected) {
			room.setInspectorUser(actor);
			room.setInspectedAt(now);
		}
		room.setUpdatedAt(now);

		return housekeepingRoomMapper.toResponse(roomRepository.save(room));
	}

	private void ensureStayoverBooking(Room room, Booking booking) {
		if (!room.getId().equals(booking.getRoom().getId())) {
			throw new BadRequestException("Booking does not belong to room: " + room.getId());
		}
		if (!EnumSet.of(BookingStatus.checked_in).contains(booking.getStatus())) {
			throw new BadRequestException("Stayover cleaning requires a checked-in booking");
		}
	}

	private ServiceRequest getStayoverForUpdate(UUID requestId) {
		return serviceRequestRepository.findByIdAndTypeForUpdate(requestId, ServiceRequestType.housekeeping)
				.orElseThrow(() -> new ResourceNotFoundException("Stayover cleaning not found: " + requestId));
	}

	private StayoverCleaningResponse toStayoverResponse(ServiceRequest request) {
		Room room = request.getRoom();
		return new StayoverCleaningResponse(
				request.getId(),
				request.getBooking().getId(),
				room != null ? room.getId() : null,
				room != null ? room.getRoomNumber() : null,
				request.getStatus(),
				request.getDescription(),
				request.getNotes(),
				request.getResponsibleUser() != null ? request.getResponsibleUser().getEmail() : null,
				request.getRequestedAt(),
				request.getStartedAt(),
				request.getCompletedAt(),
				request.getCreatedAt(),
				request.getUpdatedAt()
		);
	}

	private User requireActor(String actorEmail) {
		if (actorEmail == null) {
			throw new InsufficientAuthenticationException("Authenticated user not found");
		}
		return userRepository.findByEmail(actorEmail)
				.orElseThrow(() -> new InsufficientAuthenticationException("Authenticated user not found"));
	}

	private User findActor(String actorEmail) {
		if (actorEmail == null) {
			throw new InsufficientAuthenticationException("Authenticated user not found");
		}
		return userRepository.findByEmail(actorEmail).orElse(null);
	}

	private static String trimToDefault(String value, String defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? defaultValue : trimmed;
	}

	private Room getRoom(UUID roomId) {
		return roomRepository.findById(roomId)
				.orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
	}

	private Room getRoomForUpdate(UUID roomId) {
		return roomRepository.findByIdForUpdate(roomId)
				.orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
	}
}
