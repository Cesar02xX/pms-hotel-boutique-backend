package com.aurora.pms.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.RoomFeatureResponse;
import com.aurora.pms.service.RoomFeatureService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/room-features")
@Tag(name = "Room Features", description = "Catálogo de características de habitación (solo consulta)")
public class RoomFeatureController {

	private final RoomFeatureService roomFeatureService;

	public RoomFeatureController(RoomFeatureService roomFeatureService) {
		this.roomFeatureService = roomFeatureService;
	}

	@GetMapping
	@Operation(summary = "List room features")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Room features found"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid token",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<RoomFeatureResponse>> findAll() {
		return ResponseEntity.ok(roomFeatureService.findAll());
	}
}
