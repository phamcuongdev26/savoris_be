package com.savoris;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestDatabaseConfig.class)
class SavorisApplicationTests {

	@DynamicPropertySource
	static void jwtKeys(DynamicPropertyRegistry registry) throws Exception {
		TestJwtKeys.register(registry);
	}

	@Test
	void contextLoads() {
	}

}
