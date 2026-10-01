package com.aurora.pms.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateRoomTypeRequest;
import com.aurora.pms.dto.request.UpdateRoomTypeRequest;
import com.aurora.pms.dto.response.RoomTypeResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.RoomTypeMapper;
import com.aurora.pms.model.RoomFeature;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.RoomTypeFeature;
import com.aurora.pms.model.RoomTypeFeatureId;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.RoomFeatureRepository;
import com.aurora.pms.repository.RoomTypeFeatureRepository;
import com.aurora.pms.repository.RoomTypeRepository;
import com.aurora.pms.service.RoomTypeService;

@Service
public class RoomTypeServiceImpl implements RoomTypeService {

	private static final Set<BookingStatus> ACTIVE_OR_FUTURE_CAPACITY_STATUSES = Set.of(
			BookingStatus.pending,
			BookingStatus.confirmed,
			BookingStatus.checked_in
	);

	private final RoomTypeRepository roomTypeRepository;
	private final RoomFeatureRepository roomFeatureRepository;
	private final RoomTypeFeatureRepository roomTypeFeatureRepository;
	private final BookingRepository bookingRepository;
	private final RoomTypeMapper roomTypeMapper;
	private final Clock clock;
	private final ZoneId hotelZoneId;

	public RoomTypeServiceImpl(
			RoomTypeRepository roomTypeRepository,
			RoomFeatureRepository roomFeatureRepository,
			RoomTypeFeatureRepository roomTypeFeatureRepository,
			BookingRepository bookingRepository,
			RoomTypeMapper roomTypeMapper,
			Clock clock,
			@Value("${pms.hotel.zone-id}") String hotelZoneId
	) {
		this.roomTypeRepository = roomTypeRepository;
		this.roomFeatureRepository = roomFeatureRepository;
		this.roomTypeFeatureRepository = roomTypeFeatureRepository;
		this.bookingRepository = bookingRepository;
		this.roomTypeMapper = roomTypeMapper;
		this.clock = clock;
		this.hotelZoneId = ZoneId.of(hotelZoneId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoomTypeResponse> findAll() {
		List<RoomType> roomTypes = roomTypeRepository.findAll(Sort.by("name"));
		if (roomTypes.isEmpty()) {
			return List.of();
		}

		// Una sola consulta para todas las asociaciones, agrupadas por tipo.
		List<UUID> roomTypeIds = roomTypes.stream().map(RoomType::getId).toList();
		Map<UUID, List<UUID>> featureIdsByRoomType = roomTypeFeatureRepository.findByIdRoomTypeIdIn(roomTypeIds)
				.stream()
				.collect(Collectors.groupingBy(
						association -> association.getId().getRoomTypeId(),
						Collectors.mapping(association -> association.getId().getRoomFeatureId(), Collectors.toList())
				));

		return roomTypes.stream()
				.map(roomType -> roomTypeMapper.toResponse(
						roomType,
						featureIdsByRoomType.getOrDefault(roomType.getId(), List.of())
				))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public RoomTypeResponse findById(UUID id) {
		RoomType roomType = getRoomType(id);
		return roomTypeMapper.toResponse(roomType, findFeatureIds(id));
	}

	@Override
	@Transactional
	public RoomTypeResponse create(CreateRoomTypeRequest request) {
		String code = request.code().trim();
		if (roomTypeRepository.existsByCode(code)) {
			throw new BadRequestException("Room type code already exists: " + code);
		}
		List<RoomFeature> roomFeatures = resolveRoomFeatures(request.roomFeatureIds());

		RoomType roomType = roomTypeMapper.toEntity(request);
		OffsetDateTime now = OffsetDateTime.now();
		roomType.setCreatedAt(now);
		roomType.setUpdatedAt(now);
		RoomType saved = roomTypeRepository.save(roomType);

		roomTypeFeatureRepository.saveAll(roomFeatures.stream()
				.map(roomFeature -> buildAssociation(saved, roomFeature))
				.toList());

		return roomTypeMapper.toResponse(saved, roomFeatures.stream().map(RoomFeature::getId).toList());
	}

	@Override
	@Transactional
	public RoomTypeResponse update(UUID id, UpdateRoomTypeRequest request) {
		RoomType roomType = getRoomType(id);

		if (request.code() != null) {
			String code = request.code().trim();
			if (roomTypeRepository.existsByCodeAndIdNot(code, id)) {
				throw new BadRequestException("Room type code already exists: " + code);
			}
		}
		validateCapacityReduction(roomType, request.capacity());

		roomTypeMapper.applyUpdate(roomType, request);
		roomType.setUpdatedAt(OffsetDateTime.now());
		RoomType saved = roomTypeRepository.save(roomType);

		if (request.roomFeatureIds() != null) {
			replaceRoomFeatures(saved, resolveRoomFeatures(request.roomFeatureIds()));
		}

		return roomTypeMapper.toResponse(saved, findFeatureIds(id));
	}

	private RoomType getRoomType(UUID id) {
		return roomTypeRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Room type not found: " + id));
	}

	private List<UUID> findFeatureIds(UUID roomTypeId) {
		return roomTypeFeatureRepository.findByIdRoomTypeId(roomTypeId).stream()
				.map(association -> association.getId().getRoomFeatureId())
				.toList();
	}

	private void validateCapacityReduction(RoomType roomType, Integer newCapacity) {
		if (newCapacity == null || newCapacity >= roomType.getCapacity()) {
			return;
		}

		boolean hasOverCapacityBooking = bookingRepository.existsActiveOrFutureOverCapacity(
				roomType.getId(),
				ACTIVE_OR_FUTURE_CAPACITY_STATUSES,
				LocalDate.now(clock.withZone(hotelZoneId)),
				newCapacity
		);
		if (hasOverCapacityBooking) {
			throw new ConflictException("Room type capacity cannot be reduced below existing active or future bookings");
		}
	}

	/**
	 * Elimina IDs repetidos y valida que todas las características existan.
	 * Devuelve las características en el orden en que llegaron.
	 */
	private List<RoomFeature> resolveRoomFeatures(List<UUID> roomFeatureIds) {
		if (roomFeatureIds == null || roomFeatureIds.isEmpty()) {
			return List.of();
		}

		Set<UUID> uniqueIds = new LinkedHashSet<>(roomFeatureIds);
		Map<UUID, RoomFeature> found = roomFeatureRepository.findAllById(uniqueIds).stream()
				.collect(Collectors.toMap(RoomFeature::getId, roomFeature -> roomFeature));

		List<UUID> missing = uniqueIds.stream().filter(featureId -> !found.containsKey(featureId)).toList();
		if (!missing.isEmpty()) {
			throw new BadRequestException("Room features not found: " + missing);
		}

		List<RoomFeature> roomFeatures = new ArrayList<>();
		uniqueIds.forEach(featureId -> roomFeatures.add(found.get(featureId)));
		return roomFeatures;
	}

	/**
	 * Sincroniza RoomTypeFeature con la lista recibida: borra las asociaciones
	 * que ya no están y agrega solo las nuevas, sin duplicar las existentes.
	 */
	private void replaceRoomFeatures(RoomType roomType, List<RoomFeature> roomFeatures) {
		List<RoomTypeFeature> current = roomTypeFeatureRepository.findByIdRoomTypeId(roomType.getId());
		Set<UUID> targetIds = roomFeatures.stream().map(RoomFeature::getId).collect(Collectors.toSet());
		Set<UUID> currentIds = current.stream()
				.map(association -> association.getId().getRoomFeatureId())
				.collect(Collectors.toSet());

		roomTypeFeatureRepository.deleteAll(current.stream()
				.filter(association -> !targetIds.contains(association.getId().getRoomFeatureId()))
				.toList());

		roomTypeFeatureRepository.saveAll(roomFeatures.stream()
				.filter(roomFeature -> !currentIds.contains(roomFeature.getId()))
				.map(roomFeature -> buildAssociation(roomType, roomFeature))
				.toList());
	}

	private RoomTypeFeature buildAssociation(RoomType roomType, RoomFeature roomFeature) {
		RoomTypeFeatureId associationId = new RoomTypeFeatureId();
		associationId.setRoomTypeId(roomType.getId());
		associationId.setRoomFeatureId(roomFeature.getId());

		RoomTypeFeature association = new RoomTypeFeature();
		association.setId(associationId);
		association.setRoomType(roomType);
		association.setRoomFeature(roomFeature);
		return association;
	}
}
