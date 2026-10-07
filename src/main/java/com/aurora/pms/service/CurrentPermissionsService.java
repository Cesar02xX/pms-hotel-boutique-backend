package com.aurora.pms.service;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.model.Role;
import com.aurora.pms.repository.RolePermissionRepository;
import com.aurora.pms.repository.RoleRepository;
import com.aurora.pms.security.GuestPrincipal;

@Service
public class CurrentPermissionsService {

	private final RoleRepository roleRepository;
	private final RolePermissionRepository rolePermissionRepository;

	public CurrentPermissionsService(RoleRepository roleRepository, RolePermissionRepository rolePermissionRepository) {
		this.roleRepository = roleRepository;
		this.rolePermissionRepository = rolePermissionRepository;
	}

	@Transactional(readOnly = true)
	public List<String> findFor(Object principal) {
		if (principal instanceof GuestPrincipal) {
			Role guestRole = roleRepository.findByCode("guest").orElse(null);
			if (guestRole == null) return List.of();
			return rolePermissionRepository.findByRoleIdWithPermission(guestRole.getId()).stream()
					.map(rolePermission -> rolePermission.getPermission().getKey())
					.sorted()
					.toList();
		}
		if (principal instanceof UserDetails userDetails) {
			return userDetails.getAuthorities().stream()
					.map(GrantedAuthority::getAuthority)
					.filter(authority -> !authority.startsWith("ROLE_"))
					.sorted()
					.toList();
		}
		return List.of();
	}
}
