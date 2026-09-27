package com.aurora.pms.security;

import com.aurora.pms.config.JwtProperties;
import com.aurora.pms.model.RefreshToken;
import com.aurora.pms.model.User;
import com.aurora.pms.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

	private static final int TOKEN_BYTES = 64;

	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtProperties jwtProperties;
	private final SecureRandom secureRandom = new SecureRandom();

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.jwtProperties = jwtProperties;
	}

	@Transactional
	public String createRefreshToken(User user) {
		String plaintextToken = generatePlaintextToken();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		RefreshToken refreshToken = new RefreshToken();
		refreshToken.setUser(user);
		refreshToken.setTokenHash(hashToken(plaintextToken));
		refreshToken.setExpiresAt(now.plus(Duration.ofMillis(jwtProperties.getRefreshExpiration())));
		refreshToken.setRevokedAt(null);
		refreshToken.setCreatedAt(now);

		refreshTokenRepository.save(refreshToken);

		return plaintextToken;
	}

	public String hashToken(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 algorithm is not available", exception);
		}
	}

	private String generatePlaintextToken() {
		byte[] randomBytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(randomBytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
	}
}
