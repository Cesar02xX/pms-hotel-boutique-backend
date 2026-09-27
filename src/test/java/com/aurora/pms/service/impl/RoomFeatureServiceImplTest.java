package com.aurora.pms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.aurora.pms.dto.response.RoomFeatureResponse;
import com.aurora.pms.mapper.RoomFeatureMapper;
import com.aurora.pms.model.RoomFeature;
import com.aurora.pms.repository.RoomFeatureRepository;

@ExtendWith(MockitoExtension.class)
class RoomFeatureServiceImplTest {

	@Mock
	private RoomFeatureRepository roomFeatureRepository;

	@Test
	void findAllMapsRoomFeatures() {
		RoomFeature roomFeature = new RoomFeature();
		roomFeature.setId(UUID.randomUUID());
		roomFeature.setName("Balcony");
		roomFeature.setDescription("Private balcony");
		roomFeature.setCreatedAt(OffsetDateTime.now());
		roomFeature.setUpdatedAt(OffsetDateTime.now());
		when(roomFeatureRepository.findAll(any(Sort.class))).thenReturn(List.of(roomFeature));

		List<RoomFeatureResponse> responses =
				new RoomFeatureServiceImpl(roomFeatureRepository, new RoomFeatureMapper()).findAll();

		assertThat(responses).singleElement().satisfies(response -> {
			assertThat(response.id()).isEqualTo(roomFeature.getId());
			assertThat(response.name()).isEqualTo("Balcony");
			assertThat(response.description()).isEqualTo("Private balcony");
		});
	}

	@Test
	void findAllReturnsEmptyListWhenThereAreNoFeatures() {
		when(roomFeatureRepository.findAll(any(Sort.class))).thenReturn(List.of());

		assertThat(new RoomFeatureServiceImpl(roomFeatureRepository, new RoomFeatureMapper()).findAll()).isEmpty();
	}
}
