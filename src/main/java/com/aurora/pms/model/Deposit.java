package com.aurora.pms.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aurora.pms.model.enums.DepositStatus;
import com.aurora.pms.model.enums.DepositMethod;

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
@Table(name = "deposits")
@Getter
@Setter
@NoArgsConstructor
public class Deposit {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "guest_id", nullable = false)
	private Guest guest;

	@Column(name = "amount_cents", nullable = false)
	private Long amountCents;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, columnDefinition = "char(3)")
	private String currency = "GTQ";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DepositMethod method;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DepositStatus status;

	@Column(name = "collected_at", nullable = false)
	private OffsetDateTime collectedAt;

	@Column(name = "refunded_at")
	private OffsetDateTime refundedAt;

	@Column(columnDefinition = "text")
	private String notes;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
}
