package com.aurora.pms.security;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.aurora.pms.model.enums.MediaTarget;

/**
 * POST /api/v1/media es genérico: el destino llega en el cuerpo, así que el
 * permiso no se puede fijar en SecurityConfig. Se exige el mismo permiso que
 * ya protege la escritura (o lectura) de cada catálogo, sin crear uno nuevo.
 */
@Component
public class MediaAccessPolicy {

	private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

	private static final Map<MediaTarget, String> WRITE_PERMISSIONS = Map.of(
			MediaTarget.room_type, SecurityPermissions.ROOM_TYPES_WRITE,
			MediaTarget.product, SecurityPermissions.ROOM_SERVICE_WRITE,
			MediaTarget.amenity, SecurityPermissions.ROOMS_WRITE
	);

	private static final Map<MediaTarget, String> READ_PERMISSIONS = Map.of(
			MediaTarget.room_type, SecurityPermissions.ROOM_TYPES_READ,
			MediaTarget.product, SecurityPermissions.ROOM_SERVICE_READ,
			MediaTarget.amenity, SecurityPermissions.ROOMS_READ
	);

	public void requireWrite(MediaTarget target) {
		require(WRITE_PERMISSIONS.get(target), "Not allowed to manage images for " + target);
	}

	/** Leer incluye a quien puede escribir: subir una imagen y no poder verla no tendría sentido. */
	public void requireRead(MediaTarget target) {
		Set<String> authorities = currentAuthorities();
		if (authorities.contains(ADMIN_AUTHORITY)
				|| authorities.contains(READ_PERMISSIONS.get(target))
				|| authorities.contains(WRITE_PERMISSIONS.get(target))) {
			return;
		}
		throw new AccessDeniedException("Not allowed to read images for " + target);
	}

	private void require(String permission, String message) {
		Set<String> authorities = currentAuthorities();
		if (!authorities.contains(ADMIN_AUTHORITY) && !authorities.contains(permission)) {
			throw new AccessDeniedException(message);
		}
	}

	private Set<String> currentAuthorities() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			return Set.of();
		}
		return authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.toSet());
	}
}
