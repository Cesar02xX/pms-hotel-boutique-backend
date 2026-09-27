package com.aurora.pms.service;

import java.util.List;

import com.aurora.pms.dto.response.RoomFeatureResponse;

public interface RoomFeatureService {

	List<RoomFeatureResponse> findAll();
}
