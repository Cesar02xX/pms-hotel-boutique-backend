package com.aurora.pms.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityConfigTest {

	@Test
	void passwordEncoderUsesBCrypt() {
		PasswordEncoder passwordEncoder = new SecurityConfig().passwordEncoder();

		String encoded = passwordEncoder.encode("s3cr3t");

		assertThat(encoded).startsWith("$2");
		assertThat(passwordEncoder.matches("s3cr3t", encoded)).isTrue();
		assertThat(passwordEncoder.matches("wrong", encoded)).isFalse();
	}
}
