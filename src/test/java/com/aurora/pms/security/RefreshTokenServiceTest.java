package com.aurora.pms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aurora.pms.config.JwtProperties;
import com.aurora.pms.model.RefreshToken;
import com.aurora.pms.model.User;
import com.aurora.pms.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Captor
	private ArgumentCaptor<RefreshToken> refreshTokenCaptor;

	@Test
	void refreshTokenPlaintextIsNotPersistedAndHashIsPersisted() {
		RefreshTokenService refreshTokenService = refreshTokenService();
		when(refreshTokenRepository.save(any(RefreshToken.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		String plaintextToken = refreshTokenService.createRefreshToken(new User());

		org.mockito.Mockito.verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
		RefreshToken savedToken = refreshTokenCaptor.getValue();

		assertThat(savedToken.getTokenHash()).isNotEqualTo(plaintextToken);
		assertThat(savedToken.getTokenHash()).isEqualTo(refreshTokenService.hashToken(plaintextToken));
		assertThat(savedToken.getRevokedAt()).isNull();
		assertThat(savedToken.getExpiresAt()).isAfter(savedToken.getCreatedAt());
	}

	@Test
	void tokenHashIsDeterministicSha256() {
		RefreshTokenService refreshTokenService = refreshTokenService();

		String firstHash = refreshTokenService.hashToken("refresh-token-value");
		String secondHash = refreshTokenService.hashToken("refresh-token-value");

		assertThat(firstHash).isEqualTo(secondHash);
		assertThat(firstHash).hasSize(64);
	}

	private RefreshTokenService refreshTokenService() {
		JwtProperties properties = new JwtProperties();
		properties.setSecret("01234567890123456789012345678901");
		properties.setAccessExpiration(1_800_000);
		properties.setRefreshExpiration(604_800_000);
		return new RefreshTokenService(refreshTokenRepository, properties);
	}
}
