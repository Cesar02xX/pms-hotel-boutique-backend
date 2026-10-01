package com.aurora.pms.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.request.CreateInventoryMovementRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.InventoryItemResponse;
import com.aurora.pms.dto.response.InventoryMovementResponse;
import com.aurora.pms.service.InventoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/inventory/items")
@Tag(name = "Inventory", description = "Existencias de inventario y sus movimientos")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class InventoryController {

	private final InventoryService inventoryService;

	public InventoryController(InventoryService inventoryService) {
		this.inventoryService = inventoryService;
	}

	@GetMapping
	@Operation(summary = "List inventory items",
			description = "Optional filters. category is matched case-insensitively. lowStock=true returns items "
					+ "with currentQuantity <= minimumQuantity; lowStock=false returns the rest.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Inventory items found"),
			@ApiResponse(responseCode = "400", description = "Invalid filter value",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<InventoryItemResponse>> findItems(
			@Parameter(description = "Filter by active flag") @RequestParam(required = false) Boolean active,
			@Parameter(description = "Filter by category") @RequestParam(required = false) String category,
			@Parameter(description = "Filter by low stock") @RequestParam(required = false) Boolean lowStock
	) {
		return ResponseEntity.ok(inventoryService.findItems(active, category, lowStock));
	}

	@GetMapping("/{itemId}")
	@Operation(summary = "Get an inventory item")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Inventory item found"),
			@ApiResponse(responseCode = "404", description = "Inventory item not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<InventoryItemResponse> findItem(@PathVariable UUID itemId) {
		return ResponseEntity.ok(inventoryService.findItem(itemId));
	}

	@GetMapping("/{itemId}/movements")
	@Operation(summary = "List inventory item movements")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Movements found"),
			@ApiResponse(responseCode = "404", description = "Inventory item not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<InventoryMovementResponse>> findMovements(@PathVariable UUID itemId) {
		return ResponseEntity.ok(inventoryService.findMovements(itemId));
	}

	@PostMapping("/{itemId}/movements")
	@Operation(summary = "Register an inventory movement",
			description = "in adds to currentQuantity (reasons: purchase, restock); out subtracts from it "
					+ "(reasons: consumption, sale, shrinkage). physical_count can be used with either type "
					+ "as a traceable adjustment. Stock cannot go negative. occurredAt, createdAt and "
					+ "responsibleUser are set by the server. Product stock is not modified.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Movement registered"),
			@ApiResponse(responseCode = "400",
					description = "Invalid request, invalid type/reason, inactive item or insufficient stock",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Inventory item not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<InventoryMovementResponse> createMovement(
			@PathVariable UUID itemId,
			@Valid @RequestBody CreateInventoryMovementRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		String actorEmail = currentUser != null ? currentUser.getUsername() : null;
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(inventoryService.createMovement(itemId, request, actorEmail));
	}
}
