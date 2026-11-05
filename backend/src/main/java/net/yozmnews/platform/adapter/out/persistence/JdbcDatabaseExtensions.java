package net.yozmnews.platform.adapter.out.persistence;

import java.util.Optional;

import net.yozmnews.platform.domain.DatabaseExtensions;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link DatabaseExtensions} 의 PostgreSQL 구현 — 시스템 카탈로그 {@code pg_extension} 을 읽는다. docs/specs/platform.md §4
 * <p>
 * 확장 이름은 카탈로그에서 고유하므로 행은 0 또는 1 이다.
 */
@Repository
class JdbcDatabaseExtensions implements DatabaseExtensions {

	private final JdbcClient jdbc;

	JdbcDatabaseExtensions(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<String> installedVersion(String name) {
		return this.jdbc.sql("select extversion from pg_extension where extname = :name")
			.param("name", name)
			.query(String.class)
			.optional();
	}

}
