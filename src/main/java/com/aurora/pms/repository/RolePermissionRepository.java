package com.aurora.pms.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.model.RolePermission;
import com.aurora.pms.model.RolePermissionId;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

	@Query("""
			SELECT rp
			FROM RolePermission rp
			JOIN FETCH rp.permission
			WHERE rp.role.id = :roleId
			""")
	List<RolePermission> findByRoleIdWithPermission(@Param("roleId") UUID roleId);

	@Modifying
	@Transactional
	void deleteByIdRoleId(UUID roleId);
}
