package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateHousekeepingChecklistItemRequest;
import com.aurora.pms.dto.request.CreateHousekeepingChecklistRequest;
import com.aurora.pms.dto.request.UpdateHousekeepingChecklistItemRequest;
import com.aurora.pms.dto.request.UpdateHousekeepingChecklistRequest;
import com.aurora.pms.dto.response.HousekeepingChecklistItemResponse;
import com.aurora.pms.dto.response.HousekeepingChecklistResponse;
import com.aurora.pms.dto.response.StayoverCleaningResponse;
import com.aurora.pms.dto.response.HousekeepingRoomResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.HousekeepingRoomMapper;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.HousekeepingChecklist;
import com.aurora.pms.model.HousekeepingChecklistItem;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.ServiceRequest;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.HousekeepingChecklistStatus;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.HousekeepingChecklistRepository;
import com.aurora.pms.repository.RoomRepository;
import com.aurora.pms.repository.ServiceRequestRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.service.HousekeepingService;

@Service
public class HousekeepingServiceImpl implements HousekeepingService {

	private final RoomRepository roomRepository;
	private final BookingRepository bookingRepository;
	private final ServiceRequestRepository serviceRequestRepository;
	private final HousekeepingChecklistRepository housekeepingChecklistRepository;
	private final UserRepository userRepository;
	private final HousekeepingRoomMapper housekeepingRoomMapper;

	public HousekeepingServiceImpl(
			RoomRepository roomRepository,
			BookingRepository bookingRepository,
			ServiceRequestRepository serviceRequestRepository,
			HousekeepingChecklistRepository housekeepingChecklistRepository,
			UserRepository userRepository,
			HousekeepingRoomMapper housekeepingRoomMapper
	) {
		this.roomRepository = roomRepository;
		this.bookingRepository = bookingRepository;
		this.serviceRequestRepository = serviceRequestRepository;
		this.housekeepingChecklistRepository = housekeepingChecklistRepository;
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
				requireActor(actorEmail)
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
				requireActor(actorEmail)
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
				requireActor(actorEmail)
		);
	}

	@Override
	@Transactional(readOnly = true)
	public List<StayoverCleaningResponse> findStayoverCleanings(UUID bookingId, ServiceRequestStatus status) {
		if (bookingId != null && !bookingRepository.existsById(bookingId)) {
			throw new ResourceNotFoundException("Booking not found: " + bookingId);
		}
		return serviceRequestRepository
				.search(ServiceRequestType.housekeeping, bookingId, status)
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
		request.setStartedByUser(requireActor(actorEmail));
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
		request.setCompletedByUser(requireActor(actorEmail));
		request.setCompletedAt(now);
		request.setUpdatedAt(now);
		return toStayoverResponse(serviceRequestRepository.save(request));
	}

	@Override
	@Transactional(readOnly = true)
	public List<HousekeepingChecklistResponse> findChecklists(
			UUID roomId,
			HousekeepingChecklistStatus status,
			UUID responsibleUserId
	) {
		if (roomId != null && !roomRepository.existsById(roomId)) {
			throw new ResourceNotFoundException("Room not found: " + roomId);
		}
		if (responsibleUserId != null && !userRepository.existsById(responsibleUserId)) {
			throw new ResourceNotFoundException("Responsible user not found: " + responsibleUserId);
		}

		return housekeepingChecklistRepository.search(roomId, status, responsibleUserId)
				.stream()
				.map(this::toChecklistResponse)
				.toList();
	}

	@Override
	@Transactional
	public HousekeepingChecklistResponse createChecklist(CreateHousekeepingChecklistRequest request, String actorEmail) {
		User actor = requireActor(actorEmail);
		ServiceRequest serviceRequest = serviceRequestRepository
				.findByIdAndTypeForUpdate(request.serviceRequestId(), ServiceRequestType.housekeeping)
				.orElseThrow(() -> new ResourceNotFoundException(
						"Housekeeping request not found: " + request.serviceRequestId()
				));

		if (housekeepingChecklistRepository.existsByServiceRequestId(serviceRequest.getId())) {
			throw new ConflictException("Checklist already exists for housekeeping request: " + serviceRequest.getId());
		}
		if (serviceRequest.getRoom() == null) {
			throw new ConflictException("Housekeeping request must be assigned to a room");
		}
		if (EnumSet.of(ServiceRequestStatus.completed, ServiceRequestStatus.cancelled, ServiceRequestStatus.rejected)
				.contains(serviceRequest.getStatus())) {
			throw new ConflictException("Cannot create checklist for request status: " + serviceRequest.getStatus());
		}

		OffsetDateTime now = OffsetDateTime.now();
		HousekeepingChecklist checklist = new HousekeepingChecklist();
		checklist.setServiceRequest(serviceRequest);
		checklist.setRoom(serviceRequest.getRoom());
		checklist.setResponsibleUser(actor);
		checklist.setStatus(HousekeepingChecklistStatus.pending);
		checklist.setObservations(trimToNull(request.observations()));
		checklist.setCreatedAt(now);
		checklist.setUpdatedAt(now);

		for (int index = 0; index < request.items().size(); index++) {
			checklist.getItems().add(toNewItem(checklist, request.items().get(index), index, actor, now));
		}

		return toChecklistResponse(housekeepingChecklistRepository.save(checklist));
	}

	@Override
	@Transactional
	public HousekeepingChecklistResponse updateChecklist(
			UUID id,
			UpdateHousekeepingChecklistRequest request,
			String actorEmail
	) {
		User actor = requireActor(actorEmail);
		HousekeepingChecklist checklist = housekeepingChecklistRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new ResourceNotFoundException("Housekeeping checklist not found: " + id));
		OffsetDateTime now = OffsetDateTime.now();

		if (request.observations() != null) {
			checklist.setObservations(trimToNull(request.observations()));
		}
		if (request.items() != null) {
			updateItems(checklist, request.items(), actor, now);
		}
		if (request.status() != null && request.status() != checklist.getStatus()) {
			applyChecklistStatus(checklist, request.status(), actor, now);
		}

		checklist.setUpdatedAt(now);
		return toChecklistResponse(housekeepingChecklistRepository.save(checklist));
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
			room.setCleaningCompletedByUser(null);
			room.setCleaningCompletedAt(null);
			room.setInspectorUser(null);
			room.setInspectedAt(null);
		}
		if (next == RoomHousekeepingStatus.clean) {
			room.setCleaningCompletedByUser(actor);
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
				request.getStartedByUser() != null ? request.getStartedByUser().getEmail() : null,
				request.getCompletedByUser() != null ? request.getCompletedByUser().getEmail() : null,
				request.getRequestedAt(),
				request.getStartedAt(),
				request.getCompletedAt(),
				request.getCreatedAt(),
				request.getUpdatedAt()
		);
	}

	private HousekeepingChecklistItem toNewItem(
			HousekeepingChecklist checklist,
			CreateHousekeepingChecklistItemRequest request,
			int position,
			User actor,
			OffsetDateTime now
	) {
		HousekeepingChecklistItem item = new HousekeepingChecklistItem();
		item.setChecklist(checklist);
		item.setLabel(request.label().trim());
		item.setChecked(Boolean.TRUE.equals(request.checked()));
		item.setPosition(position);
		item.setNotes(trimToNull(request.notes()));
		if (item.isChecked()) {
			item.setCheckedAt(now);
			item.setCheckedByUser(actor);
		}
		item.setCreatedAt(now);
		item.setUpdatedAt(now);
		return item;
	}

	private HousekeepingChecklistItem toNewItem(
			HousekeepingChecklist checklist,
			UpdateHousekeepingChecklistItemRequest request,
			int position,
			User actor,
			OffsetDateTime now
	) {
		HousekeepingChecklistItem item = new HousekeepingChecklistItem();
		item.setChecklist(checklist);
		applyItemUpdate(item, request.label(), Boolean.TRUE.equals(request.checked()), request.notes(), actor, now);
		item.setPosition(position);
		item.setCreatedAt(now);
		return item;
	}

	private void updateItems(
			HousekeepingChecklist checklist,
			List<UpdateHousekeepingChecklistItemRequest> itemRequests,
			User actor,
			OffsetDateTime now
	) {
		Map<UUID, HousekeepingChecklistItem> existingById = checklist.getItems()
				.stream()
				.filter(item -> item.getId() != null)
				.collect(Collectors.toMap(HousekeepingChecklistItem::getId, Function.identity()));

		for (int index = 0; index < itemRequests.size(); index++) {
			UpdateHousekeepingChecklistItemRequest itemRequest = itemRequests.get(index);
			HousekeepingChecklistItem item;
			if (itemRequest.id() == null) {
				item = toNewItem(checklist, itemRequest, index, actor, now);
				checklist.getItems().add(item);
				continue;
			}

			item = existingById.get(itemRequest.id());
			if (item == null) {
				throw new ResourceNotFoundException("Checklist item not found: " + itemRequest.id());
			}
			applyItemUpdate(
					item,
					itemRequest.label(),
					Boolean.TRUE.equals(itemRequest.checked()),
					itemRequest.notes(),
					actor,
					now
			);
			item.setPosition(index);
		}
	}

	private void applyItemUpdate(
			HousekeepingChecklistItem item,
			String label,
			boolean checked,
			String notes,
			User actor,
			OffsetDateTime now
	) {
		item.setLabel(label.trim());
		if (item.isChecked() != checked) {
			item.setChecked(checked);
			item.setCheckedAt(checked ? now : null);
			item.setCheckedByUser(checked ? actor : null);
		}
		item.setNotes(trimToNull(notes));
		item.setUpdatedAt(now);
	}

	private void applyChecklistStatus(
			HousekeepingChecklist checklist,
			HousekeepingChecklistStatus next,
			User actor,
			OffsetDateTime now
	) {
		HousekeepingChecklistStatus current = checklist.getStatus();
		if (!isValidChecklistTransition(current, next)) {
			throw new ConflictException("Cannot move checklist from status " + current + " to " + next);
		}
		if (next == HousekeepingChecklistStatus.completed && checklist.getItems().stream().anyMatch(item -> !item.isChecked())) {
			throw new ConflictException("Cannot complete checklist with unchecked items");
		}

		checklist.setStatus(next);
		if (next == HousekeepingChecklistStatus.in_progress && checklist.getStartedAt() == null) {
			checklist.setStartedAt(now);
		}
		if (next == HousekeepingChecklistStatus.completed) {
			if (checklist.getStartedAt() == null) {
				checklist.setStartedAt(now);
			}
			checklist.setCompletedByUser(actor);
			checklist.setCompletedAt(now);
		}
		if (next == HousekeepingChecklistStatus.cancelled) {
			checklist.setCompletedByUser(null);
			checklist.setCompletedAt(null);
		}
	}

	private boolean isValidChecklistTransition(
			HousekeepingChecklistStatus current,
			HousekeepingChecklistStatus next
	) {
		return switch (current) {
			case pending -> next == HousekeepingChecklistStatus.in_progress
					|| next == HousekeepingChecklistStatus.completed
					|| next == HousekeepingChecklistStatus.cancelled;
			case in_progress -> next == HousekeepingChecklistStatus.completed
					|| next == HousekeepingChecklistStatus.cancelled;
			case completed, cancelled -> false;
		};
	}

	private HousekeepingChecklistResponse toChecklistResponse(HousekeepingChecklist checklist) {
		Room room = checklist.getRoom();
		return new HousekeepingChecklistResponse(
				checklist.getId(),
				checklist.getServiceRequest().getId(),
				room.getId(),
				room.getRoomNumber(),
				checklist.getStatus(),
				checklist.getObservations(),
				checklist.getResponsibleUser() != null ? checklist.getResponsibleUser().getEmail() : null,
				checklist.getCompletedByUser() != null ? checklist.getCompletedByUser().getEmail() : null,
				checklist.getStartedAt(),
				checklist.getCompletedAt(),
				checklist.getCreatedAt(),
				checklist.getUpdatedAt(),
				checklist.getItems().stream()
						.filter(Objects::nonNull)
						.map(this::toChecklistItemResponse)
						.toList()
		);
	}

	private HousekeepingChecklistItemResponse toChecklistItemResponse(HousekeepingChecklistItem item) {
		return new HousekeepingChecklistItemResponse(
				item.getId(),
				item.getLabel(),
				item.isChecked(),
				item.getPosition(),
				item.getNotes(),
				item.getCheckedByUser() != null ? item.getCheckedByUser().getEmail() : null,
				item.getCheckedAt(),
				item.getCreatedAt(),
				item.getUpdatedAt()
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
			return null;
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

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
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
