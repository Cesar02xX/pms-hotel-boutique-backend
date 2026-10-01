package com.aurora.pms.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.Guest;
import com.aurora.pms.model.enums.DocumentType;

public interface GuestRepository extends JpaRepository<Guest, UUID> {

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

	boolean existsByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);

	boolean existsByDocumentTypeAndDocumentNumberAndIdNot(DocumentType documentType, String documentNumber, UUID id);
}
