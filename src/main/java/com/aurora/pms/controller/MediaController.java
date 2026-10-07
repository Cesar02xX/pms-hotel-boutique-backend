package com.aurora.pms.controller;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aurora.pms.domain.port.storage.StoredObject;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.MediaUploadResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.model.enums.MediaTarget;
import com.aurora.pms.model.enums.MediaVariant;
import com.aurora.pms.service.MediaImageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media", description = "Carga de imágenes para tipos de habitación, productos y amenidades")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
		@ApiResponse(responseCode = "403", description = "Missing permission for the image target",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class MediaController {

	private final MediaImageService mediaImageService;

	public MediaController(MediaImageService mediaImageService) {
		this.mediaImageService = mediaImageService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(
			summary = "Upload a catalog image",
			description = "Multipart with 'file' (JPEG, PNG or WebP) and 'target' (room_type, product or amenity). "
					+ "The image stays pending until its id is sent in the images list of a create/update of "
					+ "that target; pending images are deleted after the configured TTL."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Image stored and pending"),
			@ApiResponse(responseCode = "400", description = "Missing part, invalid target, empty or corrupt image",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "413", description = "Image exceeds the maximum size",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "415", description = "File is not a JPEG, PNG or WebP image",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "503", description = "Image storage unavailable; nothing was saved",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<MediaUploadResponse> upload(
			@RequestPart("file") MultipartFile file,
			@RequestParam(name = "target", required = false) MediaTarget target
	) throws IOException {
		if (target == null) {
			throw new BadRequestException("Image target is required: room_type, product or amenity");
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(mediaImageService.upload(target, file.getBytes()));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a pending image", description = "Associated images are removed from their record instead.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Image deleted"),
			@ApiResponse(responseCode = "404", description = "Image not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "Image is associated with a record",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		mediaImageService.deletePending(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/content/{variant}")
	@Operation(
			summary = "Read any image variant as staff",
			description = "For previews of pending images or images of inactive records, which the public route hides."
	)
	public ResponseEntity<byte[]> content(@PathVariable UUID id, @PathVariable MediaVariant variant) {
		StoredObject object = mediaImageService.readForStaff(id, variant);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(object.contentType()))
				.header("Cache-Control", "private, no-store")
				.header("X-Content-Type-Options", "nosniff")
				.body(object.content());
	}
}
