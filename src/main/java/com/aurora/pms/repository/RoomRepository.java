package com.aurora.pms.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.Room;

public interface RoomRepository extends JpaRepository<Room, UUID> {

	boolean existsByRoomNumber(String roomNumber);

	boolean existsByRoomNumberAndIdNot(String roomNumber, UUID id);
}
