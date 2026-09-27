package com.aurora.pms.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.RolePermission;
import com.aurora.pms.model.RolePermissionId;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
}
