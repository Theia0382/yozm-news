package net.yozmnews.platform.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import net.yozmnews.platform.domain.RegistryRows;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.EventPublication;
import org.springframework.stereotype.Repository;
import org.springframework.util.ClassUtils;

/**
 * {@link RegistryRows} 의 PostgreSQL 구현 — docs/specs/platform.md §4.
 * <p>
 * 이벤트 발행 레지스트리({@code event_publication})를 이벤트 내용 없이 읽는다. 이벤트 클래스를 로드할 수 있는지는 Spring Modulith 가 행을 읽을
 * 때와 같은 클래스 로더 · 같은 판정({@link ClassUtils})으로 본다(platform-R16).
 */
@Repository
class JdbcRegistryRows implements RegistryRows, BeanClassLoaderAware {

	/** 처리 시도 — 비었으면 0 으로 읽는다. 읽기와 조건부 삭제가 같은 식을 쓴다 */
	private static final String ATTEMPTS = "coalesce(completion_attempts, 0)";

	private static final String SELECT = """
			select id, event_type, listener_id, publication_date, status, %s as completion_attempts, last_resubmission_date
			from event_publication
			""".formatted(ATTEMPTS);

	private final JdbcClient jdbc;

	private @Nullable ClassLoader classLoader;

	JdbcRegistryRows(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	@Override
	public List<RegistryRow> findAll() {
		return this.jdbc.sql(SELECT + "order by publication_date, id").query(this::row).list();
	}

	@Override
	public List<RegistryRow> findByIds(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return this.jdbc.sql(SELECT + "where id in (:ids) order by publication_date, id")
			.param("ids", ids)
			.query(this::row)
			.list();
	}

	@Override
	public boolean deleteIfUnchanged(RegistryRow row) {
		EventPublication.Status status = row.status();
		if (status == null) {
			throw new IllegalArgumentException("상태가 없는 행은 지우지 않는다: " + row.id());
		}
		return this.jdbc.sql("""
				delete from event_publication
				where id = :id and status = :status and %s = :attempts and event_type = :eventType
				""".formatted(ATTEMPTS))
			.param("id", row.id())
			.param("status", status.name())
			.param("attempts", row.completionAttempts())
			.param("eventType", row.eventType())
			.update() == 1;
	}

	private RegistryRow row(ResultSet rs, int rowNum) throws SQLException {
		String eventType = rs.getString("event_type");
		String status = rs.getString("status");
		OffsetDateTime lastResubmission = rs.getObject("last_resubmission_date", OffsetDateTime.class);
		return new RegistryRow(rs.getObject("id", UUID.class), eventType, rs.getString("listener_id"),
				rs.getObject("publication_date", OffsetDateTime.class).toInstant(),
				(status != null) ? EventPublication.Status.valueOf(status) : null, rs.getInt("completion_attempts"),
				(lastResubmission != null) ? lastResubmission.toInstant() : null,
				ClassUtils.isPresent(eventType, this.classLoader));
	}

}
