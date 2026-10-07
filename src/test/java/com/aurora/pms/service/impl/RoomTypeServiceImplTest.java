package com.aurora.pms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.aurora.pms.dto.request.CreateRoomTypeRequest;
import com.aurora.pms.dto.request.UpdateRoomTypeRequest;
import com.aurora.pms.dto.response.RoomTypeResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.mapper.RoomTypeMapper;
import com.aurora.pms.model.RoomFeature;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.RoomTypeFeature;
import com.aurora.pms.model.RoomTypeFeatureId;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.RoomFeatureRepository;
import com.aurora.pms.repository.RoomTypeFeatureRepository;
import com.aurora.pms.repository.RoomTypeRepository;
import com.aurora.pms.service.MediaImageService;

@ExtendWith(MockitoExtension.class)
class RoomTypeServiceImplTest {

	private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T06:00:00Z"),
			ZoneId.of("America/Guatemala"));

	@Mock
	private RoomTypeRepository roomTypeRepository;

	@Mock
	private RoomFeatureRepository roomFeatureRepository;

	@Mock
	private RoomTypeFeatureRepository roomTypeFeatureRepository;

	@Mock
	private BookingRepository bookingRepository;

	@Mock
	private MediaImageService mediaImageService;

	@Captor
	private ArgumentCaptor<List<RoomTypeFeature>> associationsCaptor;

	private RoomTypeServiceImpl roomTypeService;

	@BeforeEach
	void setUp() {
		roomTypeService = new RoomTypeServiceImpl(
				roomTypeRepository,
				roomFeatureRepository,
				roomTypeFeatureRepository,
				bookingRepository,
				new RoomTypeMapper(),
				mediaImageService,
				CLOCK,
				"America/Guatemala"
		);
	}

	@Test
	void createStoresEachFeatureOnceAndSetsTimestamps() {
		RoomFeature balcony = roomFeature();
		RoomFeature jacuzzi = roomFeature();
		when(roomTypeRepository.existsByCode("DLX")).thenReturn(false);
		when(roomFeatureRepository.findAllById(Set.of(balcony.getId(), jacuzzi.getId())))
				.thenReturn(List.of(balcony, jacuzzi));
		when(roomTypeRepository.save(any(RoomType.class))).thenAnswer(invocation -> {
			RoomType saved = invocation.getArgument(0);
			saved.setId(UUID.randomUUID());
			return saved;
		});

		RoomTypeResponse response = roomTypeService.create(new CreateRoomTypeRequest(
				"DLX", "Deluxe", null, 2, null,
				List.of(balcony.getId(), jacuzzi.getId(), balcony.getId()), null, null));

		assertThat(response.roomFeatureIds()).containsExactly(balcony.getId(), jacuzzi.getId());
		assertThat(response.active()).isTrue();
		assertThat(response.createdAt()).isNotNull();
		verify(roomTypeFeatureRepository).saveAll(associationsCaptor.capture());
		assertThat(associationsCaptor.getValue()).hasSize(2);
	}

	@Test
	void createRejectsUnknownFeature() {
		UUID unknownFeatureId = UUID.randomUUID();
		when(roomTypeRepository.existsByCode("DLX")).thenReturn(false);
		when(roomFeatureRepository.findAllById(Set.of(unknownFeatureId))).thenReturn(List.of());

		assertThatThrownBy(() -> roomTypeService.create(new CreateRoomTypeRequest(
				"DLX", "Deluxe", null, 2, null, List.of(unknownFeatureId), null, null)))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining(unknownFeatureId.toString());
		verify(roomTypeRepository, never()).save(any());
	}

	@Test
	void createRejectsDuplicateCode() {
		when(roomTypeRepository.existsByCode("DLX")).thenReturn(true);

		assertThatThrownBy(() -> roomTypeService.create(new CreateRoomTypeRequest(
				"DLX", "Deluxe", null, 2, null, null, null, null)))
				.isInstanceOf(BadRequestException.class);
		verify(roomTypeRepository, never()).save(any());
	}

	@Test
	void updateOnlyRemovesAndAddsChangedAssociations() {
		RoomType roomType = roomType();
		RoomFeature kept = roomFeature();
		RoomFeature removed = roomFeature();
		RoomFeature added = roomFeature();
		RoomTypeFeature keptAssociation = association(roomType, kept);
		RoomTypeFeature removedAssociation = association(roomType, removed);

		when(roomTypeRepository.findById(roomType.getId())).thenReturn(Optional.of(roomType));
		when(roomTypeRepository.save(roomType)).thenReturn(roomType);
		when(roomFeatureRepository.findAllById(Set.of(kept.getId(), added.getId())))
				.thenReturn(List.of(kept, added));
		when(roomTypeFeatureRepository.findByIdRoomTypeId(roomType.getId()))
				.thenReturn(List.of(keptAssociation, removedAssociation));

		RoomTypeResponse response = roomTypeService.update(roomType.getId(), new UpdateRoomTypeRequest(
				null, null, null, null, null, List.of(kept.getId(), added.getId()), null, null));

		verify(roomTypeFeatureRepository).deleteAll(List.of(removedAssociation));
		verify(roomTypeFeatureRepository).saveAll(associationsCaptor.capture());
		assertThat(associationsCaptor.getValue())
				.extracting(association -> association.getId().getRoomFeatureId())
				.containsExactly(added.getId());
		assertThat(response.createdAt()).isEqualTo(CREATED_AT);
		assertThat(response.updatedAt()).isAfter(CREATED_AT);
	}

	@Test
	void updateWithoutFeatureIdsKeepsAssociations() {
		RoomType roomType = roomType();
		when(roomTypeRepository.findById(roomType.getId())).thenReturn(Optional.of(roomType));
		when(roomTypeRepository.save(roomType)).thenReturn(roomType);

		roomTypeService.update(roomType.getId(), new UpdateRoomTypeRequest(
				null, "Deluxe Plus", null, 3, null, null, null, null));

		assertThat(roomType.getName()).isEqualTo("Deluxe Plus");
		assertThat(roomType.getCapacity()).isEqualTo(3);
		verify(roomTypeFeatureRepository, never()).deleteAll(anyIterable());
		verify(roomTypeFeatureRepository, never()).saveAll(anyIterable());
	}

	@Test
	void updateAllowsCapacityReductionWhenNoActiveOrFutureBookingExceedsIt() {
		RoomType roomType = roomType();
		roomType.setCapacity(4);
		when(roomTypeRepository.findById(roomType.getId())).thenReturn(Optional.of(roomType));
		when(bookingRepository.existsActiveOrFutureOverCapacity(
				any(), anyCollection(), any(LocalDate.class), anyInt()))
				.thenReturn(false);
		when(roomTypeRepository.save(roomType)).thenReturn(roomType);

		RoomTypeResponse response = roomTypeService.update(roomType.getId(), new UpdateRoomTypeRequest(
				null, null, null, 2, null, null, null, null));

		assertThat(response.capacity()).isEqualTo(2);
		verify(bookingRepository).existsActiveOrFutureOverCapacity(
				roomType.getId(), Set.of(BookingStatus.pending, BookingStatus.confirmed, BookingStatus.checked_in),
				LocalDate.of(2026, 10, 1), 2);
	}

	@Test
	void updateRejectsCapacityReductionBelowActiveOrFutureBookingOccupancy() {
		RoomType roomType = roomType();
		roomType.setCapacity(4);
		when(roomTypeRepository.findById(roomType.getId())).thenReturn(Optional.of(roomType));
		when(bookingRepository.existsActiveOrFutureOverCapacity(
				any(), anyCollection(), any(LocalDate.class), anyInt()))
				.thenReturn(true);

		assertThatThrownBy(() -> roomTypeService.update(roomType.getId(), new UpdateRoomTypeRequest(
				null, null, null, 2, null, null, null, null)))
				.isInstanceOf(ConflictException.class);
		verify(roomTypeRepository, never()).save(any());
	}

	@Test
	void findAllLoadsAssociationsInASingleQuery() {
		RoomType first = roomType();
		RoomType second = roomType();
		RoomFeature balcony = roomFeature();
		when(roomTypeRepository.findAll(any(Sort.class)))
				.thenReturn(List.of(first, second));
		when(roomTypeFeatureRepository.findByIdRoomTypeIdIn(anyCollection()))
				.thenReturn(List.of(association(first, balcony)));

		List<RoomTypeResponse> responses = roomTypeService.findAll();

		assertThat(responses).hasSize(2);
		assertThat(responses.get(0).roomFeatureIds()).containsExactly(balcony.getId());
		assertThat(responses.get(1).roomFeatureIds()).isEmpty();
		verify(roomTypeFeatureRepository, never()).findByIdRoomTypeId(any());
	}

	private static RoomType roomType() {
		RoomType roomType = new RoomType();
		roomType.setId(UUID.randomUUID());
		roomType.setCode("DLX-" + roomType.getId());
		roomType.setName("Deluxe");
		roomType.setCapacity(2);
		roomType.setCreatedAt(CREATED_AT);
		roomType.setUpdatedAt(CREATED_AT);
		return roomType;
	}

	private static RoomFeature roomFeature() {
		RoomFeature roomFeature = new RoomFeature();
		roomFeature.setId(UUID.randomUUID());
		roomFeature.setName("Feature");
		return roomFeature;
	}

	private static RoomTypeFeature association(RoomType roomType, RoomFeature roomFeature) {
		RoomTypeFeatureId id = new RoomTypeFeatureId();
		id.setRoomTypeId(roomType.getId());
		id.setRoomFeatureId(roomFeature.getId());
		RoomTypeFeature association = new RoomTypeFeature();
		association.setId(id);
		association.setRoomType(roomType);
		association.setRoomFeature(roomFeature);
		return association;
	}
}
