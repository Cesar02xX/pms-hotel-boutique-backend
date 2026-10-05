package com.aurora.pms.dto.request;

import java.util.List;

import com.aurora.pms.model.enums.HousekeepingChecklistStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UpdateHousekeepingChecklistRequest(
		HousekeepingChecklistStatus status,

		@Size(max = 1000)
		String observations,

		@Size(max = 50)
		List<@Valid UpdateHousekeepingChecklistItemRequest> items
) {
}
