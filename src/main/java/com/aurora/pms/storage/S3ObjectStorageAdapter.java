package com.aurora.pms.storage;

import java.util.Optional;

import com.aurora.pms.domain.port.storage.ObjectStorageException;
import com.aurora.pms.domain.port.storage.ObjectStoragePort;
import com.aurora.pms.domain.port.storage.StoredObject;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * ObjectStoragePort sobre AWS S3 o cualquier servicio compatible (MinIO,
 * Cloudflare R2, DigitalOcean Spaces). El bucket es privado: la API lee los
 * objetos y los sirve; nunca entrega URLs ni credenciales del proveedor.
 */
public class S3ObjectStorageAdapter implements ObjectStoragePort {

	private final S3Client s3Client;
	private final String bucket;

	public S3ObjectStorageAdapter(S3Client s3Client, String bucket) {
		this.s3Client = s3Client;
		this.bucket = bucket;
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		try {
			s3Client.putObject(
					PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
					RequestBody.fromBytes(content)
			);
		} catch (SdkException exception) {
			throw new ObjectStorageException("Could not store object " + key, exception);
		}
	}

	@Override
	public Optional<StoredObject> get(String key) {
		try {
			ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
					GetObjectRequest.builder().bucket(bucket).key(key).build()
			);
			return Optional.of(new StoredObject(object.asByteArray(), object.response().contentType()));
		} catch (NoSuchKeyException exception) {
			return Optional.empty();
		} catch (SdkException exception) {
			throw new ObjectStorageException("Could not read object " + key, exception);
		}
	}

	@Override
	public void delete(String key) {
		try {
			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
		} catch (SdkException exception) {
			throw new ObjectStorageException("Could not delete object " + key, exception);
		}
	}
}
