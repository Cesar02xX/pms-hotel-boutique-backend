package com.aurora.pms.dto.request;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.model.enums.HousekeepingChecklistStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateHousekeepingChecklistRequest(
		UUID serviceRequestId,

		UUID roomId,

		HousekeepingChecklistStatus status,

		@Size(max = 1000)
		String observations,

		@NotEmpty
		@Size(max = 50)
		List<@Valid CreateHousekeepingChecklistItemRequest> items
) {
}
