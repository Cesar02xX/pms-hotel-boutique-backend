package com.aurora.pms.service.impl;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.response.RoomFeatureResponse;
import com.aurora.pms.mapper.RoomFeatureMapper;
import com.aurora.pms.repository.RoomFeatureRepository;
import com.aurora.pms.service.RoomFeatureService;

@Service
public class RoomFeatureServiceImpl implements RoomFeatureService {

	private final RoomFeatureRepository roomFeatureRepository;
	private final RoomFeatureMapper roomFeatureMapper;

	public RoomFeatureServiceImpl(RoomFeatureRepository roomFeatureRepository, RoomFeatureMapper roomFeatureMapper) {
		this.roomFeatureRepository = roomFeatureRepository;
		this.roomFeatureMapper = roomFeatureMapper;
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoomFeatureResponse> findAll() {
		return roomFeatureRepository.findAll(Sort.by("name")).stream()
				.map(roomFeatureMapper::toResponse)
				.toList();
	}
}
