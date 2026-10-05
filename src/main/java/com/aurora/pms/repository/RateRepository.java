package com.aurora.pms.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.Rate;

public interface RateRepository extends JpaRepository<Rate, UUID> {

	@Query("""
			select r
			from Rate r
			where r.active = true
			  and r.roomType.active = true
			  and (r.validTo is null or r.validTo >= :today)
			order by r.validFrom, r.name
			""")
	List<Rate> findActiveCurrentForActiveRoomTypes(@Param("today") LocalDate today);

	@Query("""
			select r
			from Rate r
			where r.active = true
			  and r.roomType.id in :roomTypeIds
			  and r.validFrom <= :checkIn
			  and (r.validTo is null or r.validTo >= :lastNight)
			order by r.validFrom desc
			""")
	List<Rate> findActiveCoveringStay(
			@Param("roomTypeIds") Collection<UUID> roomTypeIds,
			@Param("checkIn") LocalDate checkIn,
			@Param("lastNight") LocalDate lastNight
	);

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
