package com.aurora.pms.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.RoomType;

public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, UUID id);
}
