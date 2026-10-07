package com.aurora.pms.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.response.InventoryItemResponse;
import com.aurora.pms.service.InventoryService;

@RestController
@RequestMapping("/api/v1/housekeeping/inventory")
public class HousekeepingInventoryController {

	private final InventoryService inventoryService;

	public HousekeepingInventoryController(InventoryService inventoryService) {
		this.inventoryService = inventoryService;
	}

	@GetMapping("/items")
	public ResponseEntity<List<InventoryItemResponse>> items() {
		return ResponseEntity.ok(inventoryService.findItems(true, "housekeeping", null));
	}
}
