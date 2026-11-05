package net.yozmnews;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/** 테스트 DB — 이미지는 개발용 Compose 의 정본을 PostgreSQL 호환 이미지로 선언해 쓴다(docs/development/infrastructure.md "PostgreSQL 이미지 정본"). */
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(
				DockerImageName.parse(DevComposeFile.postgresImage()).asCompatibleSubstituteFor("postgres"));
	}

}
