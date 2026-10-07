package com.aurora.pms.controller;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.domain.port.storage.StoredObject;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.model.enums.MediaVariant;
import com.aurora.pms.service.MediaImageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Imágenes publicadas, sin sesión. URL estable por id y variante, pensada
 * para poner un CDN delante. El caché es corto porque una imagen deja de ser
 * pública si se quita de su registro o el registro se desactiva.
 */
@RestController
@RequestMapping("/api/v1/public/media")
@Tag(name = "Public Media", description = "Imágenes publicadas del catálogo")
@SecurityRequirements
public class PublicMediaController {

	private static final CacheControl PUBLIC_CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

	private final MediaImageService mediaImageService;

	public PublicMediaController(MediaImageService mediaImageService) {
		this.mediaImageService = mediaImageService;
	}

	@GetMapping("/{id}/{variant}")
	@Operation(summary = "Get a published image variant (thumb, medium or large)")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Image found"),
			@ApiResponse(responseCode = "404", description = "Image not found, pending or of an inactive record",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<byte[]> get(@PathVariable UUID id, @PathVariable MediaVariant variant) {
		StoredObject object = mediaImageService.readPublic(id, variant);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(object.contentType()))
				.cacheControl(PUBLIC_CACHE)
				.eTag("\"" + id + "-" + variant.name() + "\"")
				.header("X-Content-Type-Options", "nosniff")
				.body(object.content());
	}
}
