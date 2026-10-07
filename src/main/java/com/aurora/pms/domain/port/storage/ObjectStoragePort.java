package com.aurora.pms.domain.port.storage;

import java.util.Optional;

/**
 * Almacenamiento de objetos para archivos binarios (imágenes de catálogo).
 * Los módulos dependen solo de esta interfaz; el proveedor concreto (S3 o
 * compatible) se elige por configuración en {@code pms.media.storage}.
 *
 * Cualquier fallo del proveedor se informa con {@link ObjectStorageException},
 * nunca con excepciones propias del SDK.
 */
public interface ObjectStoragePort {

	void put(String key, byte[] content, String contentType);

	Optional<StoredObject> get(String key);

	/** Borrar una clave que no existe no es un error. */
	void delete(String key);
}
