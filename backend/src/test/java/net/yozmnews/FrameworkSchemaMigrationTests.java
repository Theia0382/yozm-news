package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jobrunr.storage.sql.common.DatabaseMigrationsProvider;
import org.jobrunr.storage.sql.common.migrations.SqlMigration;
import org.jobrunr.storage.sql.postgres.PostgresStorageProvider;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 프레임워크 테이블(JobRunr · Modulith 이벤트 발행 레지스트리)의 정본은 Flyway 마이그레이션이다. 마이그레이션은 두
 * 라이브러리의 스키마 원문을 구획으로 나눠 담는다(구획 규칙은 {@code V1__jobrunr_and_event_publication.sql} 머리 주석).
 * 라이브러리를 올려 원문이 바뀌거나 새 스크립트가 생기면 여기서 실패한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FrameworkSchemaMigrationTests {

	private static final String JOBRUNR_MIGRATION = "-- jobrunr-migration: ";

	private static final String JOBRUNR_MIGRATION_RECORD = "-- jobrunr-migration-record";

	private static final String MODULITH_SCHEMA = "-- modulith-schema: ";

	private static final String MODULITH_SCHEMA_ROOT = "org/springframework/modulith/events/jdbc/schemas/";

	@Autowired
	JdbcClient jdbc;

	@Test
	void migratesEmptyDatabase() {
		assertThat(this.jdbc.sql("select version from flyway_schema_history where success").query(String.class).list())
			.containsExactly("1");
		assertThat(this.jdbc.sql("""
				select table_name from information_schema.tables
				where table_schema = 'public' and table_type = 'BASE TABLE'
				""").query(String.class).list())
			.containsExactlyInAnyOrder("flyway_schema_history", "jobrunr_jobs", "jobrunr_recurring_jobs",
					"jobrunr_backgroundjobservers", "jobrunr_metadata", "jobrunr_migrations", "event_publication");
		assertThat(this.jdbc.sql("""
				select table_name from information_schema.tables
				where table_schema = 'public' and table_type = 'VIEW'
				""").query(String.class).list())
			.containsExactly("jobrunr_jobs_stats");
	}

	@Test
	void migrationSectionsMatchLibrarySources() throws IOException {
		Map<String, String> library = jobrunrMigrations();
		Sections sections = Sections.read();

		assertThat(library).isNotEmpty();
		assertThat(sections.jobrunr().keySet()).containsExactlyInAnyOrderElementsOf(library.keySet());
		library.forEach((script, source) -> assertThat(sections.jobrunr().get(script)).as(script)
			.isNotBlank()
			.isEqualTo(source.strip()));

		assertThat(sections.modulith()).containsOnlyKeys("v2/schema-postgresql.sql");
		for (Map.Entry<String, String> section : sections.modulith().entrySet()) {
			assertThat(section.getValue()).as(section.getKey())
				.isNotBlank()
				.isEqualTo(read(new ClassPathResource(MODULITH_SCHEMA_ROOT + section.getKey())).strip());
		}
	}

	@Test
	void jobrunrMigrationRecordMatchesLibrary() throws IOException {
		assertThat(this.jdbc.sql("select script from jobrunr_migrations").query(String.class).list())
			.doesNotHaveDuplicates()
			.containsExactlyInAnyOrderElementsOf(jobrunrMigrations().keySet());
	}

	/** JobRunr 가 PostgreSQL 에 적용하는 스크립트 — 스스로 테이블을 만들 때 쓰는 해석 그대로. 이름 → 원문. */
	private static Map<String, String> jobrunrMigrations() throws IOException {
		Map<String, String> migrations = new LinkedHashMap<>();
		for (SqlMigration migration : new DatabaseMigrationsProvider(PostgresStorageProvider.class).getMigrations()
			.toList()) {
			migrations.put(migration.getFileName(), migration.getMigrationSql());
		}
		return migrations;
	}

	private static String read(Resource resource) throws IOException {
		return resource.getContentAsString(StandardCharsets.UTF_8);
	}

	/** 마이그레이션 파일 전체에서 모은 구획 본문. 키는 표시 줄의 스크립트 이름 · 스키마 경로. */
	private record Sections(Map<String, String> jobrunr, Map<String, String> modulith) {

		static Sections read() throws IOException {
			Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/*.sql");
			assertThat(files).isNotEmpty();
			Sections sections = new Sections(new LinkedHashMap<>(), new LinkedHashMap<>());
			for (Resource file : files) {
				sections.collect(FrameworkSchemaMigrationTests.read(file));
			}
			return sections;
		}

		private void collect(String content) {
			@Nullable Map<String, String> target = null;
			@Nullable String key = null;
			List<String> body = new ArrayList<>();
			for (String line : content.split("\n", -1)) {
				boolean marker = line.startsWith(JOBRUNR_MIGRATION) || line.equals(JOBRUNR_MIGRATION_RECORD)
						|| line.startsWith(MODULITH_SCHEMA);
				if (!marker) {
					body.add(line);
					continue;
				}
				put(target, key, body);
				body.clear();
				if (line.startsWith(JOBRUNR_MIGRATION)) {
					target = this.jobrunr;
					key = line.substring(JOBRUNR_MIGRATION.length()).strip();
				}
				else if (line.startsWith(MODULITH_SCHEMA)) {
					target = this.modulith;
					key = line.substring(MODULITH_SCHEMA.length()).strip();
				}
				else {
					target = null;
					key = null;
				}
			}
			put(target, key, body);
		}

		private static void put(@Nullable Map<String, String> target, @Nullable String key, List<String> body) {
			if (target == null || key == null) {
				return;
			}
			assertThat(target).as("구획이 두 번 나온다: " + key).doesNotContainKey(key);
			target.put(key, String.join("\n", body).strip());
		}

	}

}
