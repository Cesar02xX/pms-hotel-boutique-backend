package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateRoomTypeRequest;
import com.aurora.pms.dto.request.UpdateRoomTypeRequest;
import com.aurora.pms.dto.response.RoomTypeResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.RoomTypeMapper;
import com.aurora.pms.model.RoomFeature;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.RoomTypeFeature;
import com.aurora.pms.model.RoomTypeFeatureId;
import com.aurora.pms.repository.RoomFeatureRepository;
import com.aurora.pms.repository.RoomTypeFeatureRepository;
import com.aurora.pms.repository.RoomTypeRepository;
import com.aurora.pms.service.RoomTypeService;

@Service
public class RoomTypeServiceImpl implements RoomTypeService {

	private final RoomTypeRepository roomTypeRepository;
	private final RoomFeatureRepository roomFeatureRepository;
	private final RoomTypeFeatureRepository roomTypeFeatureRepository;
	private final RoomTypeMapper roomTypeMapper;

	public RoomTypeServiceImpl(
			RoomTypeRepository roomTypeRepository,
			RoomFeatureRepository roomFeatureRepository,
			RoomTypeFeatureRepository roomTypeFeatureRepository,
			RoomTypeMapper roomTypeMapper
	) {
		this.roomTypeRepository = roomTypeRepository;
		this.roomFeatureRepository = roomFeatureRepository;
		this.roomTypeFeatureRepository = roomTypeFeatureRepository;
		this.roomTypeMapper = roomTypeMapper;
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
