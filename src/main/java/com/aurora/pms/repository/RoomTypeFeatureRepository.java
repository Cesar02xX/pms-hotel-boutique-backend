package com.aurora.pms.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.RoomTypeFeature;
import com.aurora.pms.model.RoomTypeFeatureId;

public interface RoomTypeFeatureRepository extends JpaRepository<RoomTypeFeature, RoomTypeFeatureId> {
}
