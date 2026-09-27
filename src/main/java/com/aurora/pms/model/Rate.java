package com.aurora.pms.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rates")
@Getter
@Setter
@NoArgsConstructor
public class Rate {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_type_id", nullable = false)
	private RoomType roomType;

	@Column(nullable = false)
	private String name;

	@Column(name = "valid_from", nullable = false)
	private LocalDate validFrom;

	@Column(name = "valid_to")
	private LocalDate validTo;

	@Column(name = "price_cents", nullable = false)
	private Long priceCents;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, columnDefinition = "char(3)")
	private String currency = "GTQ";

	@Column(name = "minimum_nights", nullable = false)
	private Integer minimumNights;

	@Column(nullable = false)
	private Boolean refundable = true;

	@Column(nullable = false)
	private Boolean active = true;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
}
