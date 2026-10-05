package com.aurora.pms.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateHousekeepingChecklistRequest(
		@NotNull
		UUID serviceRequestId,

		@Size(max = 1000)
		String observations,

		@NotEmpty
		@Size(max = 50)
		List<@Valid CreateHousekeepingChecklistItemRequest> items
) {
}
