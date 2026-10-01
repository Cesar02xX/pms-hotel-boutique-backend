package com.aurora.pms.security;

import com.aurora.pms.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JwtService {

	private final JwtProperties jwtProperties;
	private final SecretKey signingKey;

	public JwtService(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;
		this.signingKey = buildSigningKey(jwtProperties.getSecret());
	}

	public String generateAccessToken(UserDetails userDetails) {
		return generateAccessToken(userDetails, "staff", userDetails.getUsername());
	}

	public String generateGuestAccessToken(GuestPrincipal guestPrincipal) {
		return generateAccessToken(guestPrincipal, "guest", guestPrincipal.bookingId().toString());
	}

	private String generateAccessToken(UserDetails userDetails, String tokenType, String subject) {
		Instant now = Instant.now();
		Instant expiration = now.plusMillis(jwtProperties.getAccessExpiration());
		List<String> authorities = userDetails.getAuthorities().stream()
				.map(authority -> authority.getAuthority())
				.toList();

		return Jwts.builder()
				.subject(subject)
				.claim("authorities", authorities)
				.claim("type", tokenType)
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiration))
				.signWith(signingKey)
				.compact();
	}

	public String extractUsername(String token) {
		return extractAllClaims(token).getSubject();
	}

	public String extractType(String token) {
		return extractAllClaims(token).get("type", String.class);
	}

	public UUID extractGuestBookingId(String token) {
		return UUID.fromString(extractUsername(token));
	}

	public boolean isTokenValid(String token, UserDetails userDetails) {
		try {
			String username = extractUsername(token);
			return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
		} catch (JwtException | IllegalArgumentException exception) {
			return false;
		}
	}

	public boolean isGuestTokenValid(String token, GuestPrincipal guestPrincipal) {
		try {
			return "guest".equals(extractType(token))
					&& extractGuestBookingId(token).equals(guestPrincipal.bookingId())
					&& !isTokenExpired(token);
		} catch (JwtException | IllegalArgumentException exception) {
			return false;
		}
	}

	public long getAccessExpirationSeconds() {
		return jwtProperties.getAccessExpiration() / 1000;
	}

	private boolean isTokenExpired(String token) {
		return extractAllClaims(token).getExpiration().before(new Date());
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parser()
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	private SecretKey buildSigningKey(String secret) {
		if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("JWT secret must be configured with at least 32 bytes");
		}

		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}
}
