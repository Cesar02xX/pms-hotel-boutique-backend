package com.aurora.pms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurora.pms.config.JwtProperties;
import com.aurora.pms.model.RefreshToken;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.UserStatus;
import com.aurora.pms.repository.RefreshTokenRepository;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
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
	void validRefreshRotatesTokenRevokingAAndCreatingActiveB() {
		RefreshTokenService refreshTokenService = refreshTokenService();
		User user = user(UserStatus.active);
		RefreshToken refreshA = refreshToken(user, OffsetDateTime.now(ZoneOffset.UTC).plusHours(1), null);
		String hashA = refreshTokenService.hashToken("refresh-a");

		when(refreshTokenRepository.findForUpdateByTokenHash(hashA)).thenReturn(Optional.of(refreshA));
		when(refreshTokenRepository.save(any(RefreshToken.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		RefreshTokenRotation rotation = refreshTokenService.rotate("refresh-a");

		verify(refreshTokenRepository).findForUpdateByTokenHash(hashA);
		verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(refreshTokenCaptor.capture());

		RefreshToken revokedA = refreshTokenCaptor.getAllValues().get(0);
		RefreshToken refreshB = refreshTokenCaptor.getAllValues().get(1);

		assertThat(rotation.user()).isSameAs(user);
		assertThat(rotation.refreshToken()).isNotBlank();
		assertThat(revokedA).isSameAs(refreshA);
		assertThat(revokedA.getRevokedAt()).isNotNull();
		assertThat(refreshB.getUser()).isSameAs(user);
		assertThat(refreshB.getRevokedAt()).isNull();
		assertThat(refreshB.getTokenHash()).isEqualTo(refreshTokenService.hashToken(rotation.refreshToken()));
		assertThat(refreshB.getTokenHash()).isNotEqualTo(rotation.refreshToken());
	}

	@Test
	void reusedRefreshTokenIsRejectedAfterRevocation() {
		RefreshTokenService refreshTokenService = refreshTokenService();
		RefreshToken revokedToken = refreshToken(
				user(UserStatus.active),
				OffsetDateTime.now(ZoneOffset.UTC).plusHours(1),
				OffsetDateTime.now(ZoneOffset.UTC)
		);

		when(refreshTokenRepository.findForUpdateByTokenHash(refreshTokenService.hashToken("refresh-a")))
				.thenReturn(Optional.of(revokedToken));

		assertThatThrownBy(() -> refreshTokenService.rotate("refresh-a"))
				.isInstanceOf(InvalidRefreshTokenException.class)
				.hasMessage("Invalid or expired refresh token");
	}

	@Test
	void unknownRefreshTokenIsRejected() {
		RefreshTokenService refreshTokenService = refreshTokenService();

		when(refreshTokenRepository.findForUpdateByTokenHash(refreshTokenService.hashToken("unknown")))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> refreshTokenService.rotate("unknown"))
				.isInstanceOf(InvalidRefreshTokenException.class)
				.hasMessage("Invalid or expired refresh token");
	}

	@Test
	void expiredRefreshTokenIsRejected() {
		RefreshTokenService refreshTokenService = refreshTokenService();
		RefreshToken expiredToken = refreshToken(
				user(UserStatus.active),
				OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1),
				null
		);

		when(refreshTokenRepository.findForUpdateByTokenHash(refreshTokenService.hashToken("expired")))
				.thenReturn(Optional.of(expiredToken));

		assertThatThrownBy(() -> refreshTokenService.rotate("expired"))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void previouslyRevokedRefreshTokenIsRejected() {
		RefreshTokenService refreshTokenService = refreshTokenService();
		RefreshToken revokedToken = refreshToken(
				user(UserStatus.active),
				OffsetDateTime.now(ZoneOffset.UTC).plusHours(1),
				OffsetDateTime.now(ZoneOffset.UTC)
		);

		when(refreshTokenRepository.findForUpdateByTokenHash(refreshTokenService.hashToken("revoked")))
				.thenReturn(Optional.of(revokedToken));

		assertThatThrownBy(() -> refreshTokenService.rotate("revoked"))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void inactiveUserRefreshTokenIsRejected() {
		RefreshTokenService refreshTokenService = refreshTokenService();
		RefreshToken inactiveUserToken = refreshToken(
				user(UserStatus.inactive),
				OffsetDateTime.now(ZoneOffset.UTC).plusHours(1),
				null
		);

		when(refreshTokenRepository.findForUpdateByTokenHash(refreshTokenService.hashToken("inactive-user")))
				.thenReturn(Optional.of(inactiveUserToken));

		assertThatThrownBy(() -> refreshTokenService.rotate("inactive-user"))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void logoutRevokesValidRefreshToken() {
		RefreshTokenService refreshTokenService = refreshTokenService();
		RefreshToken refreshB = refreshToken(user(UserStatus.active), OffsetDateTime.now(ZoneOffset.UTC).plusHours(1), null);

		when(refreshTokenRepository.findForUpdateByTokenHash(refreshTokenService.hashToken("refresh-b")))
				.thenReturn(Optional.of(refreshB));
		when(refreshTokenRepository.save(any(RefreshToken.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		refreshTokenService.revoke("refresh-b");

		verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
		assertThat(refreshTokenCaptor.getValue()).isSameAs(refreshB);
		assertThat(refreshB.getRevokedAt()).isNotNull();
	}

	@Test
	void refreshAfterLogoutIsRejected() {
		RefreshTokenService refreshTokenService = refreshTokenService();
		RefreshToken loggedOutToken = refreshToken(
				user(UserStatus.active),
				OffsetDateTime.now(ZoneOffset.UTC).plusHours(1),
				OffsetDateTime.now(ZoneOffset.UTC)
		);

		when(refreshTokenRepository.findForUpdateByTokenHash(refreshTokenService.hashToken("refresh-b")))
				.thenReturn(Optional.of(loggedOutToken));

		assertThatThrownBy(() -> refreshTokenService.rotate("refresh-b"))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void tokenHashIsDeterministicSha256() {
		RefreshTokenService refreshTokenService = refreshTokenService();

		String firstHash = refreshTokenService.hashToken("refresh-token-value");
		String secondHash = refreshTokenService.hashToken("refresh-token-value");

		assertThat(firstHash).isEqualTo(secondHash);
		assertThat(firstHash).hasSize(64);
	}

	@Test
	void repositoryLookupForRotationUsesPessimisticWriteLock() throws NoSuchMethodException {
		Method method = RefreshTokenRepository.class.getMethod("findForUpdateByTokenHash", String.class);
		org.springframework.data.jpa.repository.Lock lock =
				method.getAnnotation(org.springframework.data.jpa.repository.Lock.class);

		assertThat(lock).isNotNull();
		assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
	}

	private RefreshTokenService refreshTokenService() {
		JwtProperties properties = new JwtProperties();
		properties.setSecret("01234567890123456789012345678901");
		properties.setAccessExpiration(1_800_000);
		properties.setRefreshExpiration(604_800_000);
		return new RefreshTokenService(refreshTokenRepository, properties);
	}

	private RefreshToken refreshToken(User user, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
		RefreshToken refreshToken = new RefreshToken();
		refreshToken.setUser(user);
		refreshToken.setTokenHash("hash");
		refreshToken.setExpiresAt(expiresAt);
		refreshToken.setRevokedAt(revokedAt);
		refreshToken.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
		return refreshToken;
	}

	private User user(UserStatus status) {
		User user = new User();
		user.setEmail("admin@aurora.test");
		user.setStatus(status);
		return user;
	}
}
