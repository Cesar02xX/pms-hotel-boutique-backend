package com.aurora.pms.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.RoomFeature;

public interface RoomFeatureRepository extends JpaRepository<RoomFeature, UUID> {
}
