package com.aurora.pms.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.GuestCredential;

public interface GuestCredentialRepository extends JpaRepository<GuestCredential, UUID> {

	Optional<GuestCredential> findByEmailIgnoreCase(String email);

	Optional<GuestCredential> findByGuestId(UUID guestId);

	boolean existsByEmailIgnoreCase(String email);
}
