package com.aurora.pms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "security.jwt.secret=01234567890123456789012345678901")
class PmsBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
