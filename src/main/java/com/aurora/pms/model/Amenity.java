package com.aurora.pms.model;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.AmenityCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "amenities")
@Getter
@Setter
@NoArgsConstructor
public class Amenity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(columnDefinition = "text")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AmenityCategory category;

	private String location;

	@Column(name = "opens_at")
	private LocalTime opensAt;

	@Column(name = "closes_at")
	private LocalTime closesAt;

	@Column(nullable = false)
	private Boolean active = true;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
}
