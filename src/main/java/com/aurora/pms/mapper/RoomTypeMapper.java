package com.aurora.pms.mapper;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.request.CreateRoomTypeRequest;
import com.aurora.pms.dto.request.UpdateRoomTypeRequest;
import com.aurora.pms.dto.response.MediaImageResponse;
import com.aurora.pms.dto.response.RoomTypeResponse;
import com.aurora.pms.model.RoomType;

@Component
public class RoomTypeMapper {

	/**
	 * Los IDs de características vienen de RoomTypeFeature; el servicio los
	 * consulta aparte porque RoomType no tiene la colección mapeada.
	 */
	public RoomTypeResponse toResponse(
			RoomType roomType,
			List<UUID> roomFeatureIds,
			List<MediaImageResponse> images
	) {
		return new RoomTypeResponse(
				roomType.getId(),
				roomType.getCode(),
				roomType.getName(),
				roomType.getDescription(),
				roomType.getCapacity(),
				roomType.getBedConfiguration(),
				List.copyOf(roomFeatureIds),
				roomType.getActive(),
				roomType.getCreatedAt(),
				roomType.getUpdatedAt(),
				List.copyOf(images)
		);
	}

	public RoomType toEntity(CreateRoomTypeRequest request) {
		RoomType roomType = new RoomType();
		roomType.setCode(request.code().trim());
		roomType.setName(request.name().trim());
		roomType.setDescription(request.description());
		roomType.setCapacity(request.capacity());
		roomType.setBedConfiguration(request.bedConfiguration());
		if (request.active() != null) {
			roomType.setActive(request.active());
		}
		return roomType;
	}

	/**
	 * Aplica solo los campos presentes en el request. Las características las
	 * gestiona el servicio mediante RoomTypeFeature.
	 */
	public void applyUpdate(RoomType roomType, UpdateRoomTypeRequest request) {
		if (request.code() != null) {
			roomType.setCode(request.code().trim());
		}
		if (request.name() != null) {
			roomType.setName(request.name().trim());
		}
		if (request.description() != null) {
			roomType.setDescription(request.description());
		}
		if (request.capacity() != null) {
			roomType.setCapacity(request.capacity());
		}
		if (request.bedConfiguration() != null) {
			roomType.setBedConfiguration(request.bedConfiguration());
		}
		if (request.active() != null) {
			roomType.setActive(request.active());
		}
	}
}
