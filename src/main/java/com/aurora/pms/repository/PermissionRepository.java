package com.aurora.pms.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.Permission;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

	List<Permission> findAllByOrderByKey();

	List<Permission> findByKeyIn(Collection<String> keys);
}
