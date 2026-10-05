package com.aurora.pms.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.HousekeepingChecklist;
import com.aurora.pms.model.enums.HousekeepingChecklistStatus;

import jakarta.persistence.LockModeType;

public interface HousekeepingChecklistRepository extends JpaRepository<HousekeepingChecklist, UUID> {

	boolean existsByServiceRequestId(UUID serviceRequestId);

	boolean existsByRoomIdAndServiceRequestIsNullAndStatusIn(
			UUID roomId,
			List<HousekeepingChecklistStatus> statuses
	);

	@EntityGraph(attributePaths = {
			"serviceRequest",
			"room",
			"responsibleUser",
			"completedByUser",
			"items",
			"items.checkedByUser"
	})
	@Query("""
			select distinct c from HousekeepingChecklist c
			left join c.room room
			left join c.responsibleUser responsible
			where (:roomId is null or room.id = :roomId)
			  and (:status is null or c.status = :status)
			  and (:responsibleUserId is null or responsible.id = :responsibleUserId)
			order by c.createdAt desc, c.updatedAt desc
			""")
	List<HousekeepingChecklist> search(
			@Param("roomId") UUID roomId,
			@Param("status") HousekeepingChecklistStatus status,
			@Param("responsibleUserId") UUID responsibleUserId
	);

	@EntityGraph(attributePaths = {
			"serviceRequest",
			"room",
			"responsibleUser",
			"completedByUser",
			"items",
			"items.checkedByUser"
	})
	@Query("select c from HousekeepingChecklist c where c.id = :id")
	Optional<HousekeepingChecklist> findDetailedById(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = {
			"serviceRequest",
			"room",
			"responsibleUser",
			"completedByUser",
			"items",
			"items.checkedByUser"
	})
	@Query("select c from HousekeepingChecklist c where c.id = :id")
	Optional<HousekeepingChecklist> findByIdForUpdate(@Param("id") UUID id);
}
