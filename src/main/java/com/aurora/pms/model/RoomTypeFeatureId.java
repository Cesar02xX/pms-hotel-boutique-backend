package com.aurora.pms.model;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class RoomTypeFeatureId implements Serializable {

	@Column(name = "room_type_id", nullable = false)
	private UUID roomTypeId;

	@Column(name = "room_feature_id", nullable = false)
	private UUID roomFeatureId;
}
