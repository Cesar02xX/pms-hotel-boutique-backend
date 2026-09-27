package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CreateRoomTypeRequest;
import com.aurora.pms.dto.request.UpdateRoomTypeRequest;
import com.aurora.pms.dto.response.RoomTypeResponse;

public interface RoomTypeService {

	List<RoomTypeResponse> findAll();

	RoomTypeResponse findById(UUID id);

	RoomTypeResponse create(CreateRoomTypeRequest request);

	RoomTypeResponse update(UUID id, UpdateRoomTypeRequest request);
}
