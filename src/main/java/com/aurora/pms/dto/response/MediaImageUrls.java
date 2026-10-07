package com.aurora.pms.dto.response;

/** URLs públicas y estables de cada variante. El original no se expone. */
public record MediaImageUrls(
		String thumb,
		String medium,
		String large
) {
}
