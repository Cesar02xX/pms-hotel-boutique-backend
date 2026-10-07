package com.aurora.pms.storage;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.aurora.pms.config.MediaProperties;
import com.aurora.pms.domain.port.storage.ObjectStoragePort;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Elige la implementación de ObjectStoragePort según
 * {@code pms.media.storage.provider}. Un proveedor nuevo agrega su propio
 * bean condicionado a otro valor, sin tocar los módulos que lo usan.
 */
@Configuration
public class ObjectStorageConfig {

	@Bean(destroyMethod = "close")
	@ConditionalOnProperty(prefix = "pms.media.storage", name = "provider", havingValue = "s3", matchIfMissing = true)
	public S3Client mediaS3Client(MediaProperties mediaProperties) {
		MediaProperties.Storage storage = mediaProperties.getStorage();
		S3ClientBuilder builder = S3Client.builder()
				.region(Region.of(storage.getRegion()))
				.forcePathStyle(storage.isPathStyleAccess());

		if (StringUtils.hasText(storage.getEndpoint())) {
			builder.endpointOverride(URI.create(storage.getEndpoint()));
		}
		if (StringUtils.hasText(storage.getAccessKey()) && StringUtils.hasText(storage.getSecretKey())) {
			builder.credentialsProvider(StaticCredentialsProvider.create(
					AwsBasicCredentials.create(storage.getAccessKey(), storage.getSecretKey())
			));
		} else {
			builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
		}
		return builder.build();
	}

	@Bean
	@ConditionalOnProperty(prefix = "pms.media.storage", name = "provider", havingValue = "s3", matchIfMissing = true)
	public ObjectStoragePort s3ObjectStorage(S3Client mediaS3Client, MediaProperties mediaProperties) {
		return new S3ObjectStorageAdapter(mediaS3Client, mediaProperties.getStorage().getBucket());
	}
}
