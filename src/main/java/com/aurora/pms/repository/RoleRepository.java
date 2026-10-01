package com.aurora.pms.repository;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.Role;

public interface RoleRepository extends JpaRepository<Role, UUID> {

	Optional<Role> findByCode(String code);
}
