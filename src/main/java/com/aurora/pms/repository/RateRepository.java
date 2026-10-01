package com.aurora.pms.repository;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.Rate;

public interface RateRepository extends JpaRepository<Rate, UUID> {

	@Query("""
			select count(r) > 0
			from Rate r
			where r.roomType.id = :roomTypeId
			  and r.validFrom <= :validTo
			  and (r.validTo is null or r.validTo >= :validFrom)
			""")
	boolean existsOverlappingRoomTypeRate(
			@Param("roomTypeId") UUID roomTypeId,
			@Param("validFrom") LocalDate validFrom,
			@Param("validTo") LocalDate validTo
	);

	@Query("""
			select count(r) > 0
			from Rate r
			where r.roomType.id = :roomTypeId
			  and (r.validTo is null or r.validTo >= :validFrom)
			""")
	boolean existsOverlappingOpenEndedRoomTypeRate(
			@Param("roomTypeId") UUID roomTypeId,
			@Param("validFrom") LocalDate validFrom
	);

	@Query("""
			select count(r) > 0
			from Rate r
			where r.roomType.id = :roomTypeId
			  and r.id <> :rateId
			  and r.validFrom <= :validTo
			  and (r.validTo is null or r.validTo >= :validFrom)
			""")
	boolean existsOverlappingRoomTypeRateExcludingId(
			@Param("roomTypeId") UUID roomTypeId,
			@Param("rateId") UUID rateId,
			@Param("validFrom") LocalDate validFrom,
			@Param("validTo") LocalDate validTo
	);

	@Query("""
			select count(r) > 0
			from Rate r
			where r.roomType.id = :roomTypeId
			  and r.id <> :rateId
			  and (r.validTo is null or r.validTo >= :validFrom)
			""")
	boolean existsOverlappingOpenEndedRoomTypeRateExcludingId(
			@Param("roomTypeId") UUID roomTypeId,
			@Param("rateId") UUID rateId,
			@Param("validFrom") LocalDate validFrom
	);
}
