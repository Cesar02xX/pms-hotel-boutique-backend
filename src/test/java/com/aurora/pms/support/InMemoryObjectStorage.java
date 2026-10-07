package com.aurora.pms.support;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.aurora.pms.domain.port.storage.ObjectStoragePort;
import com.aurora.pms.domain.port.storage.StoredObject;

/** ObjectStoragePort en memoria para las pruebas de API: no necesita un almacenamiento S3 real. */
public class InMemoryObjectStorage implements ObjectStoragePort {

	private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

	@Override
	public void put(String key, byte[] content, String contentType) {
		objects.put(key, new StoredObject(content.clone(), contentType));
	}

	@Override
	public Optional<StoredObject> get(String key) {
		return Optional.ofNullable(objects.get(key));
	}

	@Override
	public void delete(String key) {
		objects.remove(key);
	}

	public boolean contains(String key) {
		return objects.containsKey(key);
	}
}
