package com.aurora.pms.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aurora.pms.model.enums.ProductCategory;

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
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true)
	private String sku;

	@Column(nullable = false)
	private String name;

	@Column(columnDefinition = "text")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ProductCategory category;

	@Column(name = "price_cents", nullable = false)
	private Long priceCents;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, columnDefinition = "char(3)")
	private String currency = "GTQ";

	@Column(name = "stock_quantity", nullable = false)
	private Integer stockQuantity = 0;

	@Column(name = "reorder_level", nullable = false)
	private Integer reorderLevel = 0;

	@Column(nullable = false)
	private Boolean active = true;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
}
