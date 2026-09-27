package com.aurora.pms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aurora.pms.dto.request.CreateRoomRequest;
import com.aurora.pms.dto.request.UpdateRoomRequest;
import com.aurora.pms.dto.response.RoomResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.RoomMapper;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.RoomStatus;
import com.aurora.pms.repository.RoomRepository;
import com.aurora.pms.repository.RoomTypeRepository;

@ExtendWith(MockitoExtension.class)
class RoomServiceImplTest {

	private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

	@Mock
	private RoomRepository roomRepository;

	@Mock
	private RoomTypeRepository roomTypeRepository;

	private RoomServiceImpl roomService;

	@BeforeEach
	void setUp() {
		roomService = new RoomServiceImpl(roomRepository, roomTypeRepository, new RoomMapper());
	}

	@Test
	void createSetsDefaultsAndTimestamps() {
		RoomType roomType = roomType();
		when(roomRepository.existsByRoomNumber("101")).thenReturn(false);
		when(roomTypeRepository.findById(roomType.getId())).thenReturn(Optional.of(roomType));
		when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

		RoomResponse response = roomService.create(
				new CreateRoomRequest(" 101 ", roomType.getId(), 1, null, null, null));

		assertThat(response.roomNumber()).isEqualTo("101");
		assertThat(response.roomTypeId()).isEqualTo(roomType.getId());
		assertThat(response.status()).isEqualTo(RoomStatus.available);
		assertThat(response.housekeepingStatus()).isEqualTo(RoomHousekeepingStatus.dirty);
		assertThat(response.createdAt()).isNotNull();
		assertThat(response.updatedAt()).isEqualTo(response.createdAt());
	}

	@Test
	void createRejectsDuplicateRoomNumber() {
		when(roomRepository.existsByRoomNumber("101")).thenReturn(true);

		assertThatThrownBy(() -> roomService.create(
				new CreateRoomRequest("101", UUID.randomUUID(), 1, null, null, null)))
				.isInstanceOf(BadRequestException.class);
		verify(roomRepository, never()).save(any());
	}

	@Test
	void createRejectsUnknownRoomType() {
		UUID roomTypeId = UUID.randomUUID();
		when(roomRepository.existsByRoomNumber("101")).thenReturn(false);
		when(roomTypeRepository.findById(roomTypeId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> roomService.create(
				new CreateRoomRequest("101", roomTypeId, 1, null, null, null)))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("Room type not found");
		verify(roomRepository, never()).save(any());
	}

	@Test
	void findByIdThrowsNotFoundWhenRoomDoesNotExist() {
		UUID id = UUID.randomUUID();
		when(roomRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> roomService.findById(id)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void updateKeepsCreatedAtAndRefreshesUpdatedAt() {
		Room room = room(roomType());
		when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
		when(roomRepository.save(room)).thenReturn(room);

		RoomResponse response = roomService.update(room.getId(),
				new UpdateRoomRequest(null, null, null, RoomStatus.maintenance, null, null));

		assertThat(response.status()).isEqualTo(RoomStatus.maintenance);
		assertThat(response.roomNumber()).isEqualTo("101");
		assertThat(response.createdAt()).isEqualTo(CREATED_AT);
		assertThat(response.updatedAt()).isAfter(CREATED_AT);
	}

	@Test
	void updateRejectsRoomNumberUsedByAnotherRoom() {
		Room room = room(roomType());
		when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
		when(roomRepository.existsByRoomNumberAndIdNot("102", room.getId())).thenReturn(true);

		assertThatThrownBy(() -> roomService.update(room.getId(),
				new UpdateRoomRequest("102", null, null, null, null, null)))
				.isInstanceOf(BadRequestException.class);
		verify(roomRepository, never()).save(any());
	}

	private static RoomType roomType() {
		RoomType roomType = new RoomType();
		roomType.setId(UUID.randomUUID());
		roomType.setCode("STD");
		roomType.setName("Standard");
		roomType.setCapacity(2);
		return roomType;
	}

	private static Room room(RoomType roomType) {
		Room room = new Room();
		room.setId(UUID.randomUUID());
		room.setRoomNumber("101");
		room.setRoomType(roomType);
		room.setStatus(RoomStatus.available);
		room.setHousekeepingStatus(RoomHousekeepingStatus.clean);
		room.setCreatedAt(CREATED_AT);
		room.setUpdatedAt(CREATED_AT);
		return room;
	}
}
