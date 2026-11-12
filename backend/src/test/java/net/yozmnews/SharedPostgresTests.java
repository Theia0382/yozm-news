package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;

/**
 * 테스트 JVM 이 함께 쓰는 PostgreSQL 컨테이너와 컨텍스트별 데이터베이스 — 스프링 없이 실제 컨테이너에 접속해 본다
 */
class SharedPostgresTests {

	@Test
	void createsANewDatabaseOnTheSameServerEachTime() {
		URI first = address(SharedPostgres.newDatabase());
		URI second = address(SharedPostgres.newDatabase());

		assertThat(first.getPath()).isNotEqualTo(second.getPath());
		assertThat(first.getHost()).isEqualTo(second.getHost());
		assertThat(first.getPort()).isEqualTo(second.getPort());
	}

	@Test
	void aNewDatabaseIsEmptyAndApartFromTheOthers() throws SQLException {
		JdbcConnectionDetails first = SharedPostgres.newDatabase();
		JdbcConnectionDetails second = SharedPostgres.newDatabase();

		execute(first, "create table probe (id integer)");

		// 대조 — 만든 테이블을 실제로 센다
		assertThat(publicTables(first)).isEqualTo(1);
		assertThat(publicTables(second)).isZero();
	}

	@Test
	void theServerAllowsEnoughConnectionsForTheCachedContexts() throws SQLException {
		assertThat(single(SharedPostgres.newDatabase(), "show max_connections")).isEqualTo("400");
	}

	/** JDBC URL 에서 {@code jdbc:} 를 뗀 주소 */
	private static URI address(JdbcConnectionDetails database) {
		return URI.create(database.getJdbcUrl().substring("jdbc:".length()));
	}

	private static int publicTables(JdbcConnectionDetails database) throws SQLException {
		return Integer
			.parseInt(single(database, "select count(*) from information_schema.tables where table_schema = 'public'"));
	}

	private static void execute(JdbcConnectionDetails database, String sql) throws SQLException {
		try (Connection connection = connect(database); Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static String single(JdbcConnectionDetails database, String sql) throws SQLException {
		try (Connection connection = connect(database);
				Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery(sql)) {
			assertThat(rows.next()).isTrue();
			return rows.getString(1);
		}
	}

	private static Connection connect(JdbcConnectionDetails database) throws SQLException {
		return DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
	}

}
