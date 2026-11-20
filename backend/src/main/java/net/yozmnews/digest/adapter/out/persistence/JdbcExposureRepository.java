package net.yozmnews.digest.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.digest.domain.exposure.ExposureRepository;
import net.yozmnews.digest.domain.interest.ExposureRow;
import net.yozmnews.event.EventId;
import net.yozmnews.platform.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link ExposureRepository} 의 PostgreSQL 구현 — 섹션 사건 · 섹션 · 다이제스트를 잇는다. 사용자를 {@code digests_user_date_idx} 의 앞
 * 칸으로 좁히고, 섹션 · 섹션 사건은 다이제스트 ID · 섹션 ID 가 앞 칸인 유일 제약 · PK 로 따라간다. 몫은 섹션마다 행 수로 나눈다.
 * docs/specs/digest.md §4 · §6
 */
@Repository
class JdbcExposureRepository implements ExposureRepository {

	private final JdbcClient jdbc;

	JdbcExposureRepository(JdbcClient jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	@Override
	public List<ExposureRow> exposures(UserId user, Instant from, Instant to) {
		return this.jdbc.sql("""
				select d.id as digest_id, d.completed_at, e.event_id,
				       1.0 / count(*) over (partition by e.digest_section_id) as share
				from digest_section_events e
				join digest_sections s on s.id = e.digest_section_id
				join digests d on d.id = s.digest_id
				where d.user_id = :user and d.status = 'READY' and d.completed_at >= :from and d.completed_at < :to
				order by d.completed_at, d.id, e.event_id
				""")
			.param("user", user.value())
			.param("from", Timestamp.from(from))
			.param("to", Timestamp.from(to))
			.query((ResultSet rs, int row) -> new ExposureRow(rs.getLong("digest_id"), new EventId(rs.getLong("event_id")),
					rs.getTimestamp("completed_at").toInstant(), rs.getDouble("share")))
			.list();
	}

}
