package net.yozmnews;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.test.context.cache.ContextCache;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트 JVM 하나가 함께 쓰는 PostgreSQL 컨테이너 — 처음 쓸 때 한 번 띄우고, 컨텍스트마다 그 안에 빈 데이터베이스를 새로 만든다
 * (docs/development/infrastructure.md "테스트 DB"). 컨테이너는 JVM 이 끝날 때 Testcontainers 가 치운다.
 */
final class SharedPostgres {

	/** 연결 풀 하나의 최대 연결 수 — HikariCP 기본값 */
	private static final int POOL_SIZE = 10;

	/**
	 * 서버의 연결 수 한도 — 캐시에 남는 컨텍스트마다 연결 풀을 하나씩 가진다. 캐시가 가득 찬 채 새 컨텍스트가 뜨는 순간과
	 * 데이터베이스를 만드는 연결의 몫으로 풀 여덟 개만큼을 더 둔다.
	 */
	private static final int MAX_CONNECTIONS = (ContextCache.DEFAULT_MAX_CONTEXT_CACHE_SIZE + 8) * POOL_SIZE;

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	private SharedPostgres() {
	}

	/** 빈 데이터베이스를 새로 만들고 그 접속 정보를 낸다 */
	static JdbcConnectionDetails newDatabase() {
		PostgreSQLContainer container = Holder.CONTAINER;
		String database = "context_" + SEQUENCE.incrementAndGet();
		try (Connection connection = DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(),
				container.getPassword()); Statement statement = connection.createStatement()) {
			statement.execute("create database " + database);
		}
		catch (SQLException ex) {
			throw new IllegalStateException("테스트 데이터베이스를 만들지 못했다: " + database, ex);
		}
		String url = "jdbc:postgresql://%s:%d/%s".formatted(container.getHost(),
				container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT), database);
		return new Database(url, container.getUsername(), container.getPassword());
	}

	private record Database(String jdbcUrl, String username, String password) implements JdbcConnectionDetails {

		@Override
		public String getJdbcUrl() {
			return this.jdbcUrl;
		}

		@Override
		public String getUsername() {
			return this.username;
		}

		@Override
		public String getPassword() {
			return this.password;
		}

	}

	/** 처음 쓸 때 컨테이너를 띄운다 */
	private static final class Holder {

		static final PostgreSQLContainer CONTAINER = start();

		private static PostgreSQLContainer start() {
			PostgreSQLContainer container = new PostgreSQLContainer(
					DockerImageName.parse(DevComposeFile.postgresImage()).asCompatibleSubstituteFor("postgres"))
				.withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=" + MAX_CONNECTIONS);
			container.start();
			return container;
		}

	}

}
