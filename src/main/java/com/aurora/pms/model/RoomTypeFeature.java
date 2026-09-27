package com.aurora.pms.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "room_type_features")
@Getter
@Setter
@NoArgsConstructor
public class RoomTypeFeature {

	@EmbeddedId
	private RoomTypeFeatureId id;

	@MapsId("roomTypeId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_type_id", nullable = false)
	private RoomType roomType;

	@MapsId("roomFeatureId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_feature_id", nullable = false)
	private RoomFeature roomFeature;
}
