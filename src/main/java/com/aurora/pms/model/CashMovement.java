package com.aurora.pms.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aurora.pms.model.enums.CashMovementType;

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
@Table(name = "cash_movements")
@Getter
@Setter
@NoArgsConstructor
public class CashMovement {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cash_session_id", nullable = false)
	private CashSession cashSession;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CashMovementType type;

	@Column(nullable = false)
	private String concept;

	@Column(name = "amount_cents", nullable = false)
	private Long amountCents;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, columnDefinition = "char(3)")
	private String currency = "GTQ";

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "responsible_user_id")
	private User responsibleUser;

	@Column(name = "occurred_at", nullable = false)
	private OffsetDateTime occurredAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payment_id")
	private Payment payment;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;
}
