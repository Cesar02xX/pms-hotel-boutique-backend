package com.aurora.pms.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aurora.pms.model.enums.ChargeCategory;
import com.aurora.pms.model.enums.ChargeStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "charges")
@Getter
@Setter
@NoArgsConstructor
public class Charge {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private Product product;

	@Column(nullable = false, columnDefinition = "text")
	private String description;

	@Column(nullable = false)
	private Integer quantity;

	@Column(name = "unit_price_cents", nullable = false)
	private Long unitPriceCents;

	@Column(name = "amount_cents", nullable = false)
	private Long amountCents;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, columnDefinition = "char(3)")
	private String currency = "GTQ";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ChargeCategory category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ChargeStatus status;

	@Column(name = "charged_at", nullable = false)
	private OffsetDateTime chargedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by_user_id")
	private User createdByUser;

	@Column(name = "void_reason", columnDefinition = "text")
	private String voidReason;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;
}
