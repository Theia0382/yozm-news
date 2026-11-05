package net.yozmnews.platform.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import net.yozmnews.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 설치된 확장의 버전 읽기 — Flyway 가 적용된 실제 PostgreSQL(개발용 Compose 의 pgvector 이미지)에서 본다.
 * <p>
 * 기대 버전은 이미지가 가진 기본 버전을 다른 카탈로그({@code pg_available_extensions})에서 읽는다 — 이미지는 메이저 태그라 확장 패치가 오를 수 있어
 * 리터럴로 두지 않는다. 마이그레이션이 그 기본 버전을 설치했고 어댑터가 그것을 읽는다는 것을 함께 보인다.
 * <p>
 * 지키는 규칙: platform-R19
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcDatabaseExtensions.class })
class JdbcDatabaseExtensionsTests {

	@Autowired
	JdbcDatabaseExtensions extensions;

	@Autowired
	JdbcClient jdbc;

	@Test
	void readsTheVersionTheMigrationInstalled() {
		String imageDefault = this.jdbc.sql("select default_version from pg_available_extensions where name = 'vector'")
			.query(String.class)
			.single();
		assertThat(imageDefault).isNotBlank();

		assertThat(this.extensions.installedVersion("vector")).contains(imageDefault);
	}

	@Test
	void readsNothingForAnExtensionThatIsNotInstalled() {
		assertThat(this.extensions.installedVersion("yozmnews_no_such_extension")).isEqualTo(Optional.empty());
	}

}
