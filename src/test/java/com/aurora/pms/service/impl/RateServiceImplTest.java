package com.aurora.pms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class RateServiceImplTest {

	private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

	@Mock
	private RateRepository rateRepository;

	@Mock
	private RoomTypeRepository roomTypeRepository;

	private RateServiceImpl rateService;

	@BeforeEach
	void setUp() {
		rateService = new RateServiceImpl(rateRepository, roomTypeRepository, new RateMapper());
	}

	@Test
	void createSetsDefaultsAndTimestamps() {
		RoomType roomType = roomType();
		when(roomTypeRepository.findById(roomType.getId())).thenReturn(Optional.of(roomType));
		when(rateRepository.save(any(Rate.class))).thenAnswer(invocation -> invocation.getArgument(0));

		RateResponse response = rateService.create(new CreateRateRequest(
				roomType.getId(), "Base", LocalDate.of(2026, 1, 1), null, 45000L, null, 1, null, null));

		assertThat(response.roomTypeId()).isEqualTo(roomType.getId());
		assertThat(response.priceCents()).isEqualTo(45000L);
		assertThat(response.currency()).isEqualTo("GTQ");
		assertThat(response.refundable()).isTrue();
		assertThat(response.active()).isTrue();
		assertThat(response.createdAt()).isNotNull();
		assertThat(response.updatedAt()).isEqualTo(response.createdAt());
	}

	@Test
	void createRejectsValidToBeforeValidFrom() {
		assertThatThrownBy(() -> rateService.create(new CreateRateRequest(
				UUID.randomUUID(), "Base", LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1),
				45000L, null, 1, null, null)))
				.isInstanceOf(BadRequestException.class);
		verifyNoInteractions(roomTypeRepository, rateRepository);
	}

	@Test
	void createAcceptsSameDayRange() {
		RoomType roomType = roomType();
		LocalDate day = LocalDate.of(2026, 6, 1);
		when(roomTypeRepository.findById(roomType.getId())).thenReturn(Optional.of(roomType));
		when(rateRepository.save(any(Rate.class))).thenAnswer(invocation -> invocation.getArgument(0));

		RateResponse response = rateService.create(new CreateRateRequest(
				roomType.getId(), "One day", day, day, 45000L, "GTQ", 1, false, true));

		assertThat(response.validTo()).isEqualTo(day);
		assertThat(response.refundable()).isFalse();
	}

	@Test
	void createRejectsUnknownRoomType() {
		UUID roomTypeId = UUID.randomUUID();
		when(roomTypeRepository.findById(roomTypeId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> rateService.create(new CreateRateRequest(
				roomTypeId, "Base", LocalDate.of(2026, 1, 1), null, 45000L, null, 1, null, null)))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("Room type not found");
		verify(rateRepository, never()).save(any());
	}

	@Test
	void updateValidatesRangeAgainstStoredValidFrom() {
		Rate rate = rate(roomType());
		when(rateRepository.findById(rate.getId())).thenReturn(Optional.of(rate));

		assertThatThrownBy(() -> rateService.update(rate.getId(), new UpdateRateRequest(
				null, null, null, LocalDate.of(2025, 12, 31), null, null, null, null, null)))
				.isInstanceOf(BadRequestException.class);
		verify(rateRepository, never()).save(any());
	}

	@Test
	void updateKeepsCreatedAtAndRefreshesUpdatedAt() {
		Rate rate = rate(roomType());
		when(rateRepository.findById(rate.getId())).thenReturn(Optional.of(rate));
		when(rateRepository.save(rate)).thenReturn(rate);

		RateResponse response = rateService.update(rate.getId(), new UpdateRateRequest(
				null, null, null, null, 52000L, null, null, false, null));

		assertThat(response.priceCents()).isEqualTo(52000L);
		assertThat(response.refundable()).isFalse();
		assertThat(response.name()).isEqualTo("Base");
		assertThat(response.createdAt()).isEqualTo(CREATED_AT);
		assertThat(response.updatedAt()).isAfter(CREATED_AT);
	}

	@Test
	void updateThrowsNotFoundWhenRateDoesNotExist() {
		UUID id = UUID.randomUUID();
		when(rateRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> rateService.update(id, new UpdateRateRequest(
				null, null, null, null, 1000L, null, null, null, null)))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	private static RoomType roomType() {
		RoomType roomType = new RoomType();
		roomType.setId(UUID.randomUUID());
		return roomType;
	}

	private static Rate rate(RoomType roomType) {
		Rate rate = new Rate();
		rate.setId(UUID.randomUUID());
		rate.setRoomType(roomType);
		rate.setName("Base");
		rate.setValidFrom(LocalDate.of(2026, 1, 1));
		rate.setPriceCents(45000L);
		rate.setMinimumNights(1);
		rate.setCreatedAt(CREATED_AT);
		rate.setUpdatedAt(CREATED_AT);
		return rate;
	}
}
