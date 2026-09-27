package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CreateRoomRequest;
import com.aurora.pms.dto.request.UpdateRoomRequest;
import com.aurora.pms.dto.response.RoomResponse;

public interface RoomService {

	List<RoomResponse> findAll();

	RoomResponse findById(UUID id);

	RoomResponse create(CreateRoomRequest request);

	RoomResponse update(UUID id, UpdateRoomRequest request);
}
