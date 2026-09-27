package com.aurora.pms.mapper;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.response.RoomFeatureResponse;
import com.aurora.pms.model.RoomFeature;

@Component
public class RoomFeatureMapper {

	public RoomFeatureResponse toResponse(RoomFeature roomFeature) {
		return new RoomFeatureResponse(
				roomFeature.getId(),
				roomFeature.getName(),
				roomFeature.getDescription(),
				roomFeature.getCreatedAt(),
				roomFeature.getUpdatedAt()
		);
	}
}
