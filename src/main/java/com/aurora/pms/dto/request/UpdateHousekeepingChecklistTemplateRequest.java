package com.aurora.pms.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record UpdateHousekeepingChecklistTemplateRequest(
		@NotEmpty
		@Size(max = 50)
		List<@NotBlank @Size(max = 240) String> items
) {
}
