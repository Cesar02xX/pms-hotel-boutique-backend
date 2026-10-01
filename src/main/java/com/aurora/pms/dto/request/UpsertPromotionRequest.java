package com.aurora.pms.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpsertPromotionRequest(
		@NotBlank String code,
		@NotBlank String name,
		String description,
		@NotNull @Min(1) @Max(100) Integer discountPercent,
		@NotNull LocalDate validFrom,
		LocalDate validTo,
		Boolean active
) {
}
