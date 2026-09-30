package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.response.HousekeepingRoomResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.HousekeepingRoomMapper;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.repository.RoomRepository;
import com.aurora.pms.service.HousekeepingService;

@Service
public class HousekeepingServiceImpl implements HousekeepingService {

	private final RoomRepository roomRepository;
	private final HousekeepingRoomMapper housekeepingRoomMapper;

	public HousekeepingServiceImpl(RoomRepository roomRepository, HousekeepingRoomMapper housekeepingRoomMapper) {
		this.roomRepository = roomRepository;
		this.housekeepingRoomMapper = housekeepingRoomMapper;
	}

	@Override
	@Transactional(readOnly = true)
	public List<HousekeepingRoomResponse> findAll(RoomHousekeepingStatus housekeepingStatus) {
		List<Room> rooms = housekeepingStatus == null
				? roomRepository.findAllByOrderByRoomNumber()
				: roomRepository.findByHousekeepingStatusOrderByRoomNumber(housekeepingStatus);

		return rooms.stream()
				.map(housekeepingRoomMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public HousekeepingRoomResponse findById(UUID roomId) {
		return housekeepingRoomMapper.toResponse(getRoom(roomId));
	}

	@Override
	@Transactional
	public HousekeepingRoomResponse startCleaning(UUID roomId) {
		return transition(roomId, RoomHousekeepingStatus.dirty, RoomHousekeepingStatus.cleaning, "start cleaning");
	}

	@Override
	@Transactional
	public HousekeepingRoomResponse completeCleaning(UUID roomId) {
		return transition(roomId, RoomHousekeepingStatus.cleaning, RoomHousekeepingStatus.clean, "complete cleaning");
	}

	@Override
	@Transactional
	public HousekeepingRoomResponse inspect(UUID roomId) {
		return transition(roomId, RoomHousekeepingStatus.clean, RoomHousekeepingStatus.inspected, "inspect");
	}

	private HousekeepingRoomResponse transition(
			UUID roomId,
			RoomHousekeepingStatus expected,
			RoomHousekeepingStatus next,
			String action
	) {
		Room room = getRoomForUpdate(roomId);
		if (room.getHousekeepingStatus() != expected) {
			throw new BadRequestException(
					"Cannot " + action + " room from housekeeping status: " + room.getHousekeepingStatus()
			);
		}

		room.setHousekeepingStatus(next);
		room.setUpdatedAt(OffsetDateTime.now());

		return housekeepingRoomMapper.toResponse(roomRepository.save(room));
	}

	private Room getRoom(UUID roomId) {
		return roomRepository.findById(roomId)
				.orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
	}

	private Room getRoomForUpdate(UUID roomId) {
		return roomRepository.findByIdForUpdate(roomId)
				.orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
	}
}
