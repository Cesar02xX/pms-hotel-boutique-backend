package com.aurora.pms.service.impl;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateRateRequest;
import com.aurora.pms.dto.request.UpdateRateRequest;
import com.aurora.pms.dto.response.RateResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.RateMapper;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.repository.RateRepository;
import com.aurora.pms.repository.RoomTypeRepository;
import com.aurora.pms.service.RateService;

@Service
public class RateServiceImpl implements RateService {

	private final RateRepository rateRepository;
	private final RoomTypeRepository roomTypeRepository;
	private final RateMapper rateMapper;

	public RateServiceImpl(
			RateRepository rateRepository,
			RoomTypeRepository roomTypeRepository,
			RateMapper rateMapper
	) {
		this.rateRepository = rateRepository;
		this.roomTypeRepository = roomTypeRepository;
		this.rateMapper = rateMapper;
	}

	@Override
	@Transactional(readOnly = true)
	public List<RateResponse> findAll() {
		return rateRepository.findAll(Sort.by("validFrom", "name")).stream()
				.map(rateMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional
	public RateResponse create(CreateRateRequest request) {
		validateDateRange(request.validFrom(), request.validTo());

		Rate rate = rateMapper.toEntity(request, getRoomType(request.roomTypeId()));
		OffsetDateTime now = OffsetDateTime.now();
		rate.setCreatedAt(now);
		rate.setUpdatedAt(now);

		return rateMapper.toResponse(rateRepository.save(rate));
	}

	@Override
	@Transactional
	public RateResponse update(UUID id, UpdateRateRequest request) {
		Rate rate = rateRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Rate not found: " + id));

		if (request.roomTypeId() != null) {
			rate.setRoomType(getRoomType(request.roomTypeId()));
		}

		rateMapper.applyUpdate(rate, request);
		// Se valida después de aplicar el update parcial, contra los valores resultantes.
		validateDateRange(rate.getValidFrom(), rate.getValidTo());
		rate.setUpdatedAt(OffsetDateTime.now());

		return rateMapper.toResponse(rateRepository.save(rate));
	}

	private RoomType getRoomType(UUID roomTypeId) {
		return roomTypeRepository.findById(roomTypeId)
				.orElseThrow(() -> new BadRequestException("Room type not found: " + roomTypeId));
	}

	private void validateDateRange(LocalDate validFrom, LocalDate validTo) {
		if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
			throw new BadRequestException("Valid to must be on or after valid from");
		}
	}
}
