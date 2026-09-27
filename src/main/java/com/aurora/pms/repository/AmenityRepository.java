package com.aurora.pms.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.Amenity;

public interface AmenityRepository extends JpaRepository<Amenity, UUID> {
}
