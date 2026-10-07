package com.aurora.pms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;

import com.aurora.pms.config.MediaProperties;
import com.aurora.pms.domain.port.storage.ObjectStorageException;
import com.aurora.pms.domain.port.storage.ObjectStoragePort;
import com.aurora.pms.dto.request.MediaImageAssignmentRequest;
import com.aurora.pms.dto.response.MediaUploadResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.model.MediaImage;
import com.aurora.pms.model.enums.MediaTarget;
import com.aurora.pms.model.enums.MediaVariant;
import com.aurora.pms.repository.MediaImageRepository;
import com.aurora.pms.security.MediaAccessPolicy;
import com.aurora.pms.support.TestImages;

@ExtendWith(MockitoExtension.class)
class MediaImageServiceImplTest {

	private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	@Mock
	private MediaImageRepository mediaImageRepository;

	@Mock
	private ObjectStoragePort objectStorage;

	@Mock
	private MediaAccessPolicy mediaAccessPolicy;

	@Mock
	private PlatformTransactionManager transactionManager;

	private MediaProperties properties;
	private MediaImageServiceImpl service;

	@BeforeEach
	void setUp() {
		properties = new MediaProperties();
		properties.setPublicBaseUrl("https://cdn.example.test/");
		service = new MediaImageServiceImpl(
				mediaImageRepository,
				objectStorage,
				new MediaImageProcessor(properties),
				mediaAccessPolicy,
				properties,
				transactionManager,
				CLOCK
		);
	}

	// --- Carga ---

	@Test
	void uploadStoresOriginalAndVariantsThenSavesPendingImage() {
		when(mediaImageRepository.saveAndFlush(any(MediaImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

		MediaUploadResponse response = service.upload(MediaTarget.room_type, TestImages.jpeg(1200, 800));

		ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
		verify(objectStorage, times(4)).put(keys.capture(), any(byte[].class), anyString());
		assertThat(keys.getAllValues()).containsExactly(
				"media/" + response.id() + "/original",
				"media/" + response.id() + "/thumb",
				"media/" + response.id() + "/medium",
				"media/" + response.id() + "/large"
		);
		assertThat(response.target()).isEqualTo(MediaTarget.room_type);
		assertThat(response.width()).isEqualTo(1200);
		assertThat(response.urls().medium())
				.isEqualTo("https://cdn.example.test/api/v1/public/media/" + response.id() + "/medium");
		assertThat(response.expiresAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusHours(24));

		ArgumentCaptor<MediaImage> saved = ArgumentCaptor.forClass(MediaImage.class);
		verify(mediaImageRepository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().isPending()).isTrue();
		assertThat(saved.getValue().getPendingSince()).isNotNull();
	}

	@Test
	void uploadChecksPermissionBeforeProcessing() {
		doThrow(new AccessDeniedException("denied")).when(mediaAccessPolicy).requireWrite(MediaTarget.product);

		assertThatThrownBy(() -> service.upload(MediaTarget.product, TestImages.jpeg(10, 10)))
				.isInstanceOf(AccessDeniedException.class);
		verify(objectStorage, never()).put(anyString(), any(byte[].class), anyString());
	}

	@Test
	void uploadRemovesStoredObjectsWhenStorageFailsHalfway() {
		lenient().doThrow(new ObjectStorageException("down", null))
				.when(objectStorage).put(org.mockito.ArgumentMatchers.endsWith("/medium"), any(byte[].class), anyString());

		assertThatThrownBy(() -> service.upload(MediaTarget.amenity, TestImages.jpeg(100, 100)))
				.isInstanceOf(ObjectStorageException.class);

		verify(objectStorage).delete(org.mockito.ArgumentMatchers.endsWith("/original"));
		verify(objectStorage).delete(org.mockito.ArgumentMatchers.endsWith("/thumb"));
		verify(mediaImageRepository, never()).saveAndFlush(any());
	}

	@Test
	void uploadRemovesStoredObjectsWhenDatabaseFails() {
		when(mediaImageRepository.saveAndFlush(any(MediaImage.class))).thenThrow(new IllegalStateException("db down"));

		assertThatThrownBy(() -> service.upload(MediaTarget.amenity, TestImages.jpeg(100, 100)))
				.isInstanceOf(IllegalStateException.class);

		verify(objectStorage, times(4)).delete(anyString());
	}

	// --- Asociación ---

	@Test
	void replaceImagesOrdersGalleryAndMakesFirstPrimaryByDefault() {
		UUID roomTypeId = UUID.randomUUID();
		MediaImage first = pending(MediaTarget.room_type);
		MediaImage second = pending(MediaTarget.room_type);
		when(mediaImageRepository.findByIdIn(anyCollection())).thenReturn(List.of(first, second));

		service.replaceImages(MediaTarget.room_type, roomTypeId, List.of(
				new MediaImageAssignmentRequest(second.getId(), "  Vista al jardín ", null),
				new MediaImageAssignmentRequest(first.getId(), null, null)
		));

		assertThat(second.getRoomTypeId()).isEqualTo(roomTypeId);
		assertThat(second.getPosition()).isZero();
		assertThat(second.getPrimary()).isTrue();
		assertThat(second.getAltText()).isEqualTo("Vista al jardín");
		assertThat(second.getPendingSince()).isNull();
		assertThat(first.getPosition()).isEqualTo(1);
		assertThat(first.getPrimary()).isFalse();
	}

	@Test
	void replaceImagesHonorsExplicitPrimary() {
		UUID productId = UUID.randomUUID();
		MediaImage first = pending(MediaTarget.product);
		MediaImage second = pending(MediaTarget.product);
		when(mediaImageRepository.findByIdIn(anyCollection())).thenReturn(List.of(first, second));

		service.replaceImages(MediaTarget.product, productId, List.of(
				new MediaImageAssignmentRequest(first.getId(), null, false),
				new MediaImageAssignmentRequest(second.getId(), null, true)
		));

		assertThat(first.getPrimary()).isFalse();
		assertThat(second.getPrimary()).isTrue();
	}

	@Test
	void replaceImagesDetachesImagesLeftOutOfTheList() {
		UUID amenityId = UUID.randomUUID();
		MediaImage kept = owned(MediaTarget.amenity, amenityId);
		MediaImage removed = owned(MediaTarget.amenity, amenityId);
		when(mediaImageRepository.findByIdIn(anyCollection())).thenReturn(List.of(kept));
		when(mediaImageRepository.findByAmenityIdInOrderByPositionAsc(anyCollection()))
				.thenReturn(List.of(kept, removed));

		service.replaceImages(MediaTarget.amenity, amenityId,
				List.of(new MediaImageAssignmentRequest(kept.getId(), null, null)));

		assertThat(kept.getAmenityId()).isEqualTo(amenityId);
		assertThat(removed.isPending()).isTrue();
		assertThat(removed.getPendingSince()).isEqualTo(OffsetDateTime.now(CLOCK));
		assertThat(removed.getPrimary()).isFalse();
	}

	@Test
	void replaceImagesWithEmptyListRemovesAll() {
		UUID amenityId = UUID.randomUUID();
		MediaImage removed = owned(MediaTarget.amenity, amenityId);
		when(mediaImageRepository.findByIdIn(anyCollection())).thenReturn(List.of());
		when(mediaImageRepository.findByAmenityIdInOrderByPositionAsc(anyCollection())).thenReturn(List.of(removed));

		service.replaceImages(MediaTarget.amenity, amenityId, List.of());

		assertThat(removed.isPending()).isTrue();
	}

	@Test
	void replaceImagesWithNullChangesNothing() {
		service.replaceImages(MediaTarget.room_type, UUID.randomUUID(), null);

		verify(mediaImageRepository, never()).findByIdIn(anyCollection());
		verify(mediaImageRepository, never()).saveAll(any());
	}

	@Test
	void replaceImagesRejectsMoreThanConfiguredLimit() {
		properties.setMaxImagesPerRecord(2);
		List<MediaImageAssignmentRequest> images = new ArrayList<>();
		for (int index = 0; index < 3; index++) {
			images.add(new MediaImageAssignmentRequest(UUID.randomUUID(), null, null));
		}

		assertThatThrownBy(() -> service.replaceImages(MediaTarget.room_type, UUID.randomUUID(), images))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("at most 2");
	}

	@Test
	void replaceImagesRejectsDuplicatesAndSeveralPrimaries() {
		UUID id = UUID.randomUUID();
		assertThatThrownBy(() -> service.replaceImages(MediaTarget.room_type, UUID.randomUUID(), List.of(
				new MediaImageAssignmentRequest(id, null, null),
				new MediaImageAssignmentRequest(id, null, null))))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("Duplicated");

		assertThatThrownBy(() -> service.replaceImages(MediaTarget.room_type, UUID.randomUUID(), List.of(
				new MediaImageAssignmentRequest(UUID.randomUUID(), null, true),
				new MediaImageAssignmentRequest(UUID.randomUUID(), null, true))))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("primary");
	}

	@Test
	void replaceImagesRejectsUnknownImage() {
		when(mediaImageRepository.findByIdIn(anyCollection())).thenReturn(List.of());

		assertThatThrownBy(() -> service.replaceImages(MediaTarget.room_type, UUID.randomUUID(),
				List.of(new MediaImageAssignmentRequest(UUID.randomUUID(), null, null))))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("not found");
	}

	@Test
	void replaceImagesRejectsImageUploadedForAnotherTarget() {
		MediaImage productImage = pending(MediaTarget.product);
		when(mediaImageRepository.findByIdIn(anyCollection())).thenReturn(List.of(productImage));

		assertThatThrownBy(() -> service.replaceImages(MediaTarget.room_type, UUID.randomUUID(),
				List.of(new MediaImageAssignmentRequest(productImage.getId(), null, null))))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("uploaded for product");
	}

	@Test
	void replaceImagesRejectsImageOwnedByAnotherRecord() {
		MediaImage taken = owned(MediaTarget.room_type, UUID.randomUUID());
		when(mediaImageRepository.findByIdIn(anyCollection())).thenReturn(List.of(taken));

		assertThatThrownBy(() -> service.replaceImages(MediaTarget.room_type, UUID.randomUUID(),
				List.of(new MediaImageAssignmentRequest(taken.getId(), null, null))))
				.isInstanceOf(ConflictException.class);
	}

	// --- Lectura y borrado ---

	@Test
	void readPublicHidesImagesThatAreNotPublished() {
		UUID id = UUID.randomUUID();
		when(mediaImageRepository.isPublic(id)).thenReturn(false);

		assertThatThrownBy(() -> service.readPublic(id, MediaVariant.thumb))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(objectStorage, never()).get(anyString());
	}

	@Test
	void deletePendingRejectsAssociatedImage() {
		MediaImage taken = owned(MediaTarget.product, UUID.randomUUID());
		when(mediaImageRepository.findById(taken.getId())).thenReturn(Optional.of(taken));

		assertThatThrownBy(() -> service.deletePending(taken.getId()))
				.isInstanceOf(ConflictException.class);
		verify(objectStorage, never()).delete(anyString());
	}

	@Test
	void deletePendingRemovesRowAndAllObjects() {
		MediaImage image = pending(MediaTarget.product);
		when(mediaImageRepository.findById(image.getId())).thenReturn(Optional.of(image));
		when(mediaImageRepository.deleteIfPending(image.getId())).thenReturn(1);

		service.deletePending(image.getId());

		verify(objectStorage, times(4)).delete(org.mockito.ArgumentMatchers.startsWith("media/" + image.getId()));
	}

	@Test
	void cleanupDeletesExpiredImagesAndKeepsGoingWhenStorageFails() {
		MediaImage failing = pending(MediaTarget.room_type);
		MediaImage expired = pending(MediaTarget.room_type);
		OffsetDateTime threshold = OffsetDateTime.now(CLOCK).minus(Duration.ofHours(24));
		when(mediaImageRepository.findByPendingSinceBeforeOrderByPendingSinceAsc(eq(threshold), any(Limit.class)))
				.thenReturn(List.of(failing, expired));
		when(mediaImageRepository.deleteIfPendingBefore(any(UUID.class), eq(threshold))).thenReturn(1);
		lenient().doThrow(new ObjectStorageException("down", null))
				.when(objectStorage).delete("media/" + failing.getId() + "/original");

		int deleted = service.deleteExpiredPending();

		assertThat(deleted).isEqualTo(1);
		verify(objectStorage).delete("media/" + expired.getId() + "/large");
	}

	private static MediaImage pending(MediaTarget target) {
		MediaImage image = new MediaImage();
		image.setId(UUID.randomUUID());
		image.setTarget(target);
		image.assignOwner(null, OffsetDateTime.now(CLOCK).minusDays(2));
		return image;
	}

	private static MediaImage owned(MediaTarget target, UUID ownerId) {
		MediaImage image = pending(target);
		image.assignOwner(ownerId, OffsetDateTime.now(CLOCK).minusDays(1));
		image.setPosition(0);
		image.setPrimary(true);
		return image;
	}
}
