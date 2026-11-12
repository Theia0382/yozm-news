package net.yozmnews;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/**
	 * 테스트 DB — 컨텍스트마다 빈 데이터베이스 하나. 컨테이너는 테스트 JVM 이 함께 쓰고, 이미지는 개발용 Compose 의 정본을 쓴다
	 * (docs/development/infrastructure.md "테스트 DB" · "PostgreSQL 이미지 정본").
	 */
	@Bean
	JdbcConnectionDetails testDatabase() {
		return SharedPostgres.newDatabase();
	}

}
