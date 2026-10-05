package com.aurora.pms.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.Room;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.RoomStatus;

public interface RoomRepository extends JpaRepository<Room, UUID> {

	boolean existsByRoomNumber(String roomNumber);

	boolean existsByRoomNumberAndIdNot(String roomNumber, UUID id);

	List<Room> findAllByOrderByRoomNumber();

	List<Room> findByHousekeepingStatusOrderByRoomNumber(RoomHousekeepingStatus housekeepingStatus);

	List<Room> findByRoomTypeIdInAndStatusNotIn(Collection<UUID> roomTypeIds, Collection<RoomStatus> statuses);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Room r where r.id = :id")
	Optional<Room> findByIdForUpdate(@Param("id") UUID id);
}
