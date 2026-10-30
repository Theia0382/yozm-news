package net.yozmnews;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 애플리케이션 컨텍스트 기동 — 실제 PostgreSQL 로 띄운다
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class YozmnewsApplicationTests {

	@Test
	void contextLoads() {
	}

}
