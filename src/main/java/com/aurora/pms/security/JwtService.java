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
		Instant now = Instant.now();
		Instant expiration = now.plusMillis(jwtProperties.getAccessExpiration());
		List<String> authorities = userDetails.getAuthorities().stream()
				.map(authority -> authority.getAuthority())
				.toList();

		return Jwts.builder()
				.subject(userDetails.getUsername())
				.claim("authorities", authorities)
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiration))
				.signWith(signingKey)
				.compact();
	}

	public String extractUsername(String token) {
		return extractAllClaims(token).getSubject();
	}

	public boolean isTokenValid(String token, UserDetails userDetails) {
		try {
			String username = extractUsername(token);
			return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
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
