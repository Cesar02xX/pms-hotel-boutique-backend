package com.aurora.pms.security;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.model.Role;
import com.aurora.pms.model.RolePermission;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.UserStatus;
import com.aurora.pms.repository.RolePermissionRepository;
import com.aurora.pms.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;
	private final RolePermissionRepository rolePermissionRepository;

	public CustomUserDetailsService(
			UserRepository userRepository,
			RolePermissionRepository rolePermissionRepository) {
		this.userRepository = userRepository;
		this.rolePermissionRepository = rolePermissionRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));

		Set<GrantedAuthority> authorities = buildAuthorities(user.getRole());

		return org.springframework.security.core.userdetails.User.builder()
				.username(user.getEmail())
				.password(user.getPasswordHash())
				.disabled(user.getStatus() == UserStatus.inactive)
				.authorities(authorities)
				.build();
	}

	private Set<GrantedAuthority> buildAuthorities(Role role) {
		Set<GrantedAuthority> authorities = new LinkedHashSet<>();
		authorities.add(new SimpleGrantedAuthority(toRoleAuthority(role.getCode())));

		for (RolePermission rolePermission : rolePermissionRepository.findByRoleIdWithPermission(role.getId())) {
			authorities.add(new SimpleGrantedAuthority(rolePermission.getPermission().getKey()));
		}

		return authorities;
	}

	private String toRoleAuthority(String roleCode) {
		return "ROLE_" + roleCode.toUpperCase(Locale.ROOT);
	}
}
