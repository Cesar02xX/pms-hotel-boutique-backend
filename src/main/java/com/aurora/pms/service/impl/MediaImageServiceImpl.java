package com.aurora.pms.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.aurora.pms.config.MediaProperties;
import com.aurora.pms.domain.port.storage.ObjectStorageException;
import com.aurora.pms.domain.port.storage.ObjectStoragePort;
import com.aurora.pms.domain.port.storage.StoredObject;
import com.aurora.pms.dto.request.MediaImageAssignmentRequest;
import com.aurora.pms.dto.response.MediaImageResponse;
import com.aurora.pms.dto.response.MediaImageUrls;
import com.aurora.pms.dto.response.MediaUploadResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.model.MediaImage;
import com.aurora.pms.model.enums.MediaTarget;
import com.aurora.pms.model.enums.MediaVariant;
import com.aurora.pms.repository.MediaImageRepository;
import com.aurora.pms.security.MediaAccessPolicy;
import com.aurora.pms.service.MediaImageService;

@Service
public class MediaImageServiceImpl implements MediaImageService {

	private static final Logger log = LoggerFactory.getLogger(MediaImageServiceImpl.class);
	private static final String ORIGINAL_KEY = "original";
	private static final String PUBLIC_MEDIA_PATH = "/api/v1/public/media/";

	private final MediaImageRepository mediaImageRepository;
	private final ObjectStoragePort objectStorage;
	private final MediaImageProcessor mediaImageProcessor;
	private final MediaAccessPolicy mediaAccessPolicy;
	private final MediaProperties mediaProperties;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public MediaImageServiceImpl(
			MediaImageRepository mediaImageRepository,
			ObjectStoragePort objectStorage,
			MediaImageProcessor mediaImageProcessor,
			MediaAccessPolicy mediaAccessPolicy,
			MediaProperties mediaProperties,
			PlatformTransactionManager transactionManager,
			Clock clock
	) {
		this.mediaImageRepository = mediaImageRepository;
		this.objectStorage = objectStorage;
		this.mediaImageProcessor = mediaImageProcessor;
		this.mediaAccessPolicy = mediaAccessPolicy;
		this.mediaProperties = mediaProperties;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/**
	 * Primero los archivos y después la fila: si el almacenamiento falla no
	 * queda una fila apuntando a nada, y si la fila falla se borran los
	 * archivos ya subidos. Nunca se responde 201 sin que ambos existan.
	 */
	@Override
	public MediaUploadResponse upload(MediaTarget target, byte[] content) {
		mediaAccessPolicy.requireWrite(target);
		ProcessedImage processed = mediaImageProcessor.process(content);

		UUID id = UUID.randomUUID();
		List<String> storedKeys = new ArrayList<>();
		try {
			String originalKey = key(id, ORIGINAL_KEY);
			objectStorage.put(originalKey, content, processed.originalContentType());
			storedKeys.add(originalKey);
			for (MediaVariant variant : MediaVariant.values()) {
				String variantKey = key(id, variant.name());
				objectStorage.put(variantKey, processed.variants().get(variant), processed.variantContentType());
				storedKeys.add(variantKey);
			}

			OffsetDateTime now = OffsetDateTime.now(clock);
			MediaImage image = new MediaImage();
			image.setId(id);
			image.setTarget(target);
			image.setOriginalContentType(processed.originalContentType());
			image.setVariantContentType(processed.variantContentType());
			image.setSizeBytes((long) content.length);
			image.setWidth(processed.width());
			image.setHeight(processed.height());
			image.setUploadedBy(currentUsername());
			image.setCreatedAt(now);
			image.assignOwner(null, now);
			MediaImage saved = mediaImageRepository.saveAndFlush(image);

			return new MediaUploadResponse(
					saved.getId(),
					saved.getTarget(),
					saved.getVariantContentType(),
					saved.getSizeBytes(),
					saved.getWidth(),
					saved.getHeight(),
					urls(saved.getId()),
					saved.getPendingSince().plus(mediaProperties.getPendingTtl())
			);
		} catch (RuntimeException exception) {
			deleteQuietly(storedKeys);
			throw exception;
		}
	}

	@Override
	@Transactional
	public void deletePending(UUID id) {
		MediaImage image = getImage(id);
		mediaAccessPolicy.requireWrite(image.getTarget());
		if (!image.isPending() || mediaImageRepository.deleteIfPending(id) == 0) {
			throw new ConflictException("Media image is associated with a record; remove it from that record instead");
		}
		// Si el almacenamiento falla, la excepción revierte el borrado de la fila y se puede reintentar.
		deleteObjects(id);
	}

	@Override
	@Transactional(readOnly = true)
	public StoredObject readPublic(UUID id, MediaVariant variant) {
		if (!mediaImageRepository.isPublic(id)) {
			throw new ResourceNotFoundException("Media image not found: " + id);
		}
		return readVariant(id, variant);
	}

	@Override
	@Transactional(readOnly = true)
	public StoredObject readForStaff(UUID id, MediaVariant variant) {
		MediaImage image = getImage(id);
		mediaAccessPolicy.requireRead(image.getTarget());
		return readVariant(id, variant);
	}

	@Override
	@Transactional
	public void replaceImages(MediaTarget target, UUID ownerId, List<MediaImageAssignmentRequest> images) {
		if (images == null) {
			return;
		}
		int maxImages = mediaProperties.getMaxImagesPerRecord();
		if (images.size() > maxImages) {
			throw new BadRequestException("A record can have at most " + maxImages + " images");
		}

		Set<UUID> requestedIds = new LinkedHashSet<>();
		for (MediaImageAssignmentRequest image : images) {
			if (!requestedIds.add(image.mediaId())) {
				throw new BadRequestException("Duplicated media id: " + image.mediaId());
			}
		}
		long primaryCount = images.stream().filter(image -> Boolean.TRUE.equals(image.primary())).count();
		if (primaryCount > 1) {
			throw new BadRequestException("Only one image can be marked as primary");
		}

		Map<UUID, MediaImage> found = mediaImageRepository.findByIdIn(requestedIds).stream()
				.collect(Collectors.toMap(MediaImage::getId, Function.identity()));
		List<UUID> missing = requestedIds.stream().filter(id -> !found.containsKey(id)).toList();
		if (!missing.isEmpty()) {
			throw new BadRequestException("Media images not found: " + missing);
		}
		for (MediaImage image : found.values()) {
			if (image.getTarget() != target) {
				throw new BadRequestException("Media image " + image.getId() + " was uploaded for "
						+ image.getTarget() + ", not for " + target);
			}
			if (!image.isPending() && !ownerId.equals(image.getOwnerId())) {
				throw new ConflictException("Media image " + image.getId() + " already belongs to another record");
			}
		}

		OffsetDateTime now = OffsetDateTime.now(clock);
		List<MediaImage> changed = new ArrayList<>();
		for (MediaImage current : findOwned(target, List.of(ownerId))) {
			if (!requestedIds.contains(current.getId())) {
				current.assignOwner(null, now);
				changed.add(current);
			}
		}
		for (int position = 0; position < images.size(); position++) {
			MediaImageAssignmentRequest request = images.get(position);
			MediaImage image = found.get(request.mediaId());
			image.assignOwner(ownerId, now);
			image.setPosition(position);
			image.setPrimary(primaryCount == 0 ? position == 0 : Boolean.TRUE.equals(request.primary()));
			image.setAltText(StringUtils.hasText(request.altText()) ? request.altText().trim() : null);
			changed.add(image);
		}
		mediaImageRepository.saveAll(changed);
	}

	@Override
	@Transactional(readOnly = true)
	public List<MediaImageResponse> findImages(MediaTarget target, UUID ownerId) {
		return findImages(target, List.of(ownerId)).getOrDefault(ownerId, List.of());
	}

	@Override
	@Transactional(readOnly = true)
	public Map<UUID, List<MediaImageResponse>> findImages(MediaTarget target, Collection<UUID> ownerIds) {
		if (ownerIds.isEmpty()) {
			return Map.of();
		}
		return findOwned(target, new HashSet<>(ownerIds)).stream()
				.sorted(Comparator.comparing(MediaImage::getPosition))
				.collect(Collectors.groupingBy(
						MediaImage::getOwnerId,
						Collectors.mapping(this::toResponse, Collectors.toList())
				));
	}

	/**
	 * Cada imagen se borra en su propia transacción: la fila primero (solo si
	 * sigue pendiente) y luego los archivos. Si el almacenamiento falla se
	 * revierte esa fila y la próxima ejecución lo reintenta.
	 */
	@Override
	public int deleteExpiredPending() {
		OffsetDateTime threshold = OffsetDateTime.now(clock).minus(mediaProperties.getPendingTtl());
		List<MediaImage> expired = mediaImageRepository.findByPendingSinceBeforeOrderByPendingSinceAsc(
				threshold,
				Limit.of(mediaProperties.getCleanup().getBatchSize())
		);

		int deleted = 0;
		for (MediaImage image : expired) {
			try {
				Boolean removed = transactionTemplate.execute(status -> {
					if (mediaImageRepository.deleteIfPendingBefore(image.getId(), threshold) == 0) {
						return false;
					}
					deleteObjects(image.getId());
					return true;
				});
				if (Boolean.TRUE.equals(removed)) {
					deleted++;
				}
			} catch (ObjectStorageException exception) {
				log.warn("Could not delete expired media image {}; it will be retried", image.getId(), exception);
			}
		}
		return deleted;
	}

	private StoredObject readVariant(UUID id, MediaVariant variant) {
		return objectStorage.get(key(id, variant.name()))
				.orElseThrow(() -> new ResourceNotFoundException("Media image not found: " + id));
	}

	private List<MediaImage> findOwned(MediaTarget target, Collection<UUID> ownerIds) {
		return switch (target) {
			case room_type -> mediaImageRepository.findByRoomTypeIdInOrderByPositionAsc(ownerIds);
			case product -> mediaImageRepository.findByProductIdInOrderByPositionAsc(ownerIds);
			case amenity -> mediaImageRepository.findByAmenityIdInOrderByPositionAsc(ownerIds);
		};
	}

	private MediaImage getImage(UUID id) {
		return mediaImageRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Media image not found: " + id));
	}

	private void deleteObjects(UUID id) {
		objectStorage.delete(key(id, ORIGINAL_KEY));
		for (MediaVariant variant : MediaVariant.values()) {
			objectStorage.delete(key(id, variant.name()));
		}
	}

	private void deleteQuietly(List<String> keys) {
		for (String key : keys) {
			try {
				objectStorage.delete(key);
			} catch (ObjectStorageException exception) {
				log.warn("Could not roll back stored object {}", key, exception);
			}
		}
	}

	private MediaImageResponse toResponse(MediaImage image) {
		return new MediaImageResponse(
				image.getId(),
				image.getAltText(),
				image.getPosition(),
				image.getPrimary(),
				image.getWidth(),
				image.getHeight(),
				urls(image.getId())
		);
	}

	private MediaImageUrls urls(UUID id) {
		String base = mediaProperties.getPublicBaseUrl().replaceAll("/+$", "") + PUBLIC_MEDIA_PATH + id + "/";
		return new MediaImageUrls(
				base + MediaVariant.thumb.name(),
				base + MediaVariant.medium.name(),
				base + MediaVariant.large.name()
		);
	}

	private String key(UUID id, String name) {
		return mediaProperties.getStorage().getKeyPrefix() + "/" + id + "/" + name;
	}

	private String currentUsername() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication == null ? null : authentication.getName();
	}
}
