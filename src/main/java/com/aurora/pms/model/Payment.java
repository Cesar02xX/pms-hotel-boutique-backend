package com.aurora.pms.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aurora.pms.model.enums.PaymentMethod;
import com.aurora.pms.model.enums.PaymentStatus;

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
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@Column(name = "amount_cents", nullable = false)
	private Long amountCents;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, columnDefinition = "char(3)")
	private String currency = "GTQ";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentMethod method;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	@Column(name = "transaction_reference")
	private String transactionReference;

	@Column(name = "paid_at")
	private OffsetDateTime paidAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "processed_by_user_id")
	private User processedByUser;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;
}
