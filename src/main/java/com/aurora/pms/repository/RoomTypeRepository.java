package com.aurora.pms.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.RoomType;

public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, UUID id);

	List<RoomType> findByActiveTrueOrderByName();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select rt from RoomType rt where rt.id = :id")
	Optional<RoomType> findByIdForUpdate(@Param("id") UUID id);
}
