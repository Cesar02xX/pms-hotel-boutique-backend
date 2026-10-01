package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateRoomRequest;
import com.aurora.pms.dto.request.UpdateRoomRequest;
import com.aurora.pms.dto.response.RoomResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.RoomMapper;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.repository.RoomRepository;
import com.aurora.pms.repository.RoomTypeRepository;
import com.aurora.pms.service.RoomService;

@Service
public class RoomServiceImpl implements RoomService {

	private final RoomRepository roomRepository;
	private final RoomTypeRepository roomTypeRepository;
	private final RoomMapper roomMapper;

	public RoomServiceImpl(
			RoomRepository roomRepository,
			RoomTypeRepository roomTypeRepository,
			RoomMapper roomMapper
	) {
		this.roomRepository = roomRepository;
		this.roomTypeRepository = roomTypeRepository;
		this.roomMapper = roomMapper;
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoomResponse> findAll() {
		return roomRepository.findAll(Sort.by("roomNumber")).stream()
				.map(roomMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public RoomResponse findById(UUID id) {
		return roomMapper.toResponse(getRoom(id));
	}

	@Override
	@Transactional
	public RoomResponse create(CreateRoomRequest request) {
		String roomNumber = request.roomNumber().trim();
		if (roomRepository.existsByRoomNumber(roomNumber)) {
			throw new BadRequestException("Room number already exists: " + roomNumber);
		}

		Room room = roomMapper.toEntity(request, getRoomType(request.roomTypeId()));
		OffsetDateTime now = OffsetDateTime.now();
		room.setCreatedAt(now);
		room.setUpdatedAt(now);

		return roomMapper.toResponse(roomRepository.save(room));
	}

	@Override
	@Transactional
	public RoomResponse update(UUID id, UpdateRoomRequest request) {
		Room room = getRoom(id);

		if (request.housekeepingStatus() != null) {
			throw new ConflictException("Housekeeping status must be changed through Housekeeping");
		}
		if (request.roomNumber() != null) {
			String roomNumber = request.roomNumber().trim();
			if (roomRepository.existsByRoomNumberAndIdNot(roomNumber, id)) {
				throw new BadRequestException("Room number already exists: " + roomNumber);
			}
		}
		if (request.roomTypeId() != null) {
			room.setRoomType(getRoomType(request.roomTypeId()));
		}

		roomMapper.applyUpdate(room, request);
		room.setUpdatedAt(OffsetDateTime.now());

		return roomMapper.toResponse(roomRepository.save(room));
	}

	private Room getRoom(UUID id) {
		return roomRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Room not found: " + id));
	}

	private RoomType getRoomType(UUID roomTypeId) {
		return roomTypeRepository.findById(roomTypeId)
				.orElseThrow(() -> new BadRequestException("Room type not found: " + roomTypeId));
	}
}
