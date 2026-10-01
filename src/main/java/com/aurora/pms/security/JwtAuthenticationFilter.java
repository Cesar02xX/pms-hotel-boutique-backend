package com.aurora.pms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final CustomUserDetailsService customUserDetailsService;
	private final GuestPrincipalService guestPrincipalService;

	public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService customUserDetailsService) {
		this(jwtService, customUserDetailsService, null);
	}

	@Autowired
	public JwtAuthenticationFilter(
			JwtService jwtService,
			CustomUserDetailsService customUserDetailsService,
			GuestPrincipalService guestPrincipalService
	) {
		this.jwtService = jwtService;
		this.customUserDetailsService = customUserDetailsService;
		this.guestPrincipalService = guestPrincipalService;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String jwt = resolveBearerToken(request);

		if (!StringUtils.hasText(jwt) || SecurityContextHolder.getContext().getAuthentication() != null) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			String tokenType = jwtService.extractType(jwt);
			if ("guest".equals(tokenType)) {
				if (guestPrincipalService == null) {
					filterChain.doFilter(request, response);
					return;
				}
				GuestPrincipal guestPrincipal = guestPrincipalService.loadByBookingId(jwtService.extractGuestBookingId(jwt));
				if (jwtService.isGuestTokenValid(jwt, guestPrincipal)) {
					UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
							guestPrincipal,
							null,
							guestPrincipal.getAuthorities()
					);
					authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
					SecurityContextHolder.getContext().setAuthentication(authentication);
				}
				filterChain.doFilter(request, response);
				return;
			}

			String email = jwtService.extractUsername(jwt);
			UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

			if (jwtService.isTokenValid(jwt, userDetails)) {
				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
						userDetails,
						null,
						userDetails.getAuthorities()
				);
				authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		} catch (RuntimeException exception) {
			SecurityContextHolder.clearContext();
		}

		filterChain.doFilter(request, response);
	}

	private String resolveBearerToken(HttpServletRequest request) {
		String header = request.getHeader(AUTHORIZATION_HEADER);
		if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
			return null;
		}

		return header.substring(BEARER_PREFIX.length());
	}
}
