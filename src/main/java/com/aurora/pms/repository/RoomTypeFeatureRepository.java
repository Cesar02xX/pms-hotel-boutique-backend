package com.aurora.pms.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.RoomTypeFeature;
import com.aurora.pms.model.RoomTypeFeatureId;

public interface RoomTypeFeatureRepository extends JpaRepository<RoomTypeFeature, RoomTypeFeatureId> {

	List<RoomTypeFeature> findByIdRoomTypeId(UUID roomTypeId);

	List<RoomTypeFeature> findByIdRoomTypeIdIn(Collection<UUID> roomTypeIds);

	void deleteByIdRoomTypeId(UUID roomTypeId);
}
