package com.aurora.pms.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.DocumentType;

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
@Table(name = "guests")
@Getter
@Setter
@NoArgsConstructor
public class Guest {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "first_name", nullable = false)
	private String firstName;

	@Column(name = "last_name", nullable = false)
	private String lastName;

	private String email;

	private String phone;

	private String nationality;

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type")
	private DocumentType documentType;

	@Column(name = "document_number")
	private String documentNumber;

	@Column(columnDefinition = "text")
	private String notes;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
}
