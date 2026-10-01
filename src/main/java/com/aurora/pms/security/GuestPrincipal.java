package com.aurora.pms.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record GuestPrincipal(UUID bookingId, UUID guestId, String linkCode) implements UserDetails {

	public static final String ROLE_GUEST = "ROLE_GUEST";

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority(ROLE_GUEST));
	}

	@Override
	public String getPassword() {
		return "";
	}

	@Override
	public String getUsername() {
		return "guest:" + bookingId;
	}
}
