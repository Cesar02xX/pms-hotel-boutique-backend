package com.aurora.pms.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * Configuración de imágenes de catálogo (#82). Todos los valores llegan por
 * variables de entorno documentadas en docs/development/media-images.md.
 */
@Component
@ConfigurationProperties(prefix = "pms.media")
public class MediaProperties {

	/** Origen con el que se arman las URLs públicas: la API o un CDN delante de ella. */
	private String publicBaseUrl = "http://localhost:8080";
	private DataSize maxFileSize = DataSize.ofMegabytes(5);
	private int maxImagesPerRecord = 10;
	/** Tope de píxeles antes de decodificar, para frenar imágenes que explotan en memoria. */
	private long maxPixels = 24_000_000L;
	/** Tiempo que una imagen sin asociar sobrevive antes de que la limpieza la borre. */
	private Duration pendingTtl = Duration.ofHours(24);
	private final Cleanup cleanup = new Cleanup();
	private final Storage storage = new Storage();

	public String getPublicBaseUrl() {
		return publicBaseUrl;
	}

	public void setPublicBaseUrl(String publicBaseUrl) {
		this.publicBaseUrl = publicBaseUrl;
	}

	public DataSize getMaxFileSize() {
		return maxFileSize;
	}

	public void setMaxFileSize(DataSize maxFileSize) {
		this.maxFileSize = maxFileSize;
	}

	public int getMaxImagesPerRecord() {
		return maxImagesPerRecord;
	}

	public void setMaxImagesPerRecord(int maxImagesPerRecord) {
		this.maxImagesPerRecord = maxImagesPerRecord;
	}

	public long getMaxPixels() {
		return maxPixels;
	}

	public void setMaxPixels(long maxPixels) {
		this.maxPixels = maxPixels;
	}

	public Duration getPendingTtl() {
		return pendingTtl;
	}

	public void setPendingTtl(Duration pendingTtl) {
		this.pendingTtl = pendingTtl;
	}

	public Cleanup getCleanup() {
		return cleanup;
	}

	public Storage getStorage() {
		return storage;
	}

	public static class Cleanup {

		private boolean enabled = true;
		private int batchSize = 100;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public int getBatchSize() {
			return batchSize;
		}

		public void setBatchSize(int batchSize) {
			this.batchSize = batchSize;
		}
	}

	public static class Storage {

		/** Implementación de ObjectStoragePort a usar. Hoy solo existe "s3" (AWS S3 o compatible). */
		private String provider = "s3";
		private String bucket = "pms-media";
		private String region = "us-east-1";
		/** Vacío en AWS S3; la URL del servicio en RustFS, MinIO, R2, Spaces, etc. */
		private String endpoint;
		/** Vacíos para usar la cadena de credenciales por defecto de AWS (rol IAM, perfil, variables AWS_*). */
		private String accessKey;
		private String secretKey;
		/** RustFS y MinIO necesitan rutas tipo /bucket/clave en vez de subdominios. */
		private boolean pathStyleAccess;
		private String keyPrefix = "media";

		public String getProvider() {
			return provider;
		}

		public void setProvider(String provider) {
			this.provider = provider;
		}

		public String getBucket() {
			return bucket;
		}

		public void setBucket(String bucket) {
			this.bucket = bucket;
		}

		public String getRegion() {
			return region;
		}

		public void setRegion(String region) {
			this.region = region;
		}

		public String getEndpoint() {
			return endpoint;
		}

		public void setEndpoint(String endpoint) {
			this.endpoint = endpoint;
		}

		public String getAccessKey() {
			return accessKey;
		}

		public void setAccessKey(String accessKey) {
			this.accessKey = accessKey;
		}

		public String getSecretKey() {
			return secretKey;
		}

		public void setSecretKey(String secretKey) {
			this.secretKey = secretKey;
		}

		public boolean isPathStyleAccess() {
			return pathStyleAccess;
		}

		public void setPathStyleAccess(boolean pathStyleAccess) {
			this.pathStyleAccess = pathStyleAccess;
		}

		public String getKeyPrefix() {
			return keyPrefix;
		}

		public void setKeyPrefix(String keyPrefix) {
			this.keyPrefix = keyPrefix;
		}
	}
}
