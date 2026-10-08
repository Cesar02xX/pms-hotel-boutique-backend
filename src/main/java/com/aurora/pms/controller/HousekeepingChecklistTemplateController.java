package com.aurora.pms.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.request.UpdateHousekeepingChecklistTemplateRequest;
import com.aurora.pms.dto.response.HousekeepingChecklistTemplateResponse;
import com.aurora.pms.service.HousekeepingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/housekeeping/checklist-template")
public class HousekeepingChecklistTemplateController {

	private final HousekeepingService housekeepingService;

	public HousekeepingChecklistTemplateController(HousekeepingService housekeepingService) {
		this.housekeepingService = housekeepingService;
	}

	@GetMapping
	public ResponseEntity<HousekeepingChecklistTemplateResponse> getTemplate() {
		return ResponseEntity.ok(housekeepingService.getGuestCleaningChecklistTemplate());
	}

	@PutMapping
	public ResponseEntity<HousekeepingChecklistTemplateResponse> updateTemplate(
			@Valid @RequestBody UpdateHousekeepingChecklistTemplateRequest request
	) {
		return ResponseEntity.ok(housekeepingService.updateGuestCleaningChecklistTemplate(request));
	}
}
