package com.aurora.pms.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.aurora.pms.service.MediaImageService;

/**
 * Borra las imágenes que nadie asoció (o que se quitaron de su registro)
 * pasado {@code pms.media.pending-ttl}. Se apaga con
 * {@code PMS_MEDIA_CLEANUP_ENABLED=false}, por ejemplo si en producción
 * corre más de una instancia y la limpieza la hace solo una.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "pms.media.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MediaCleanupJob {

	private static final Logger log = LoggerFactory.getLogger(MediaCleanupJob.class);

	private final MediaImageService mediaImageService;

	public MediaCleanupJob(MediaImageService mediaImageService) {
		this.mediaImageService = mediaImageService;
	}

	@Scheduled(
			initialDelayString = "${pms.media.cleanup.initial-delay:PT5M}",
			fixedDelayString = "${pms.media.cleanup.interval:PT1H}"
	)
	public void deleteExpiredPendingImages() {
		int deleted = mediaImageService.deleteExpiredPending();
		if (deleted > 0) {
			log.info("Deleted {} expired pending media images", deleted);
		}
	}
}
