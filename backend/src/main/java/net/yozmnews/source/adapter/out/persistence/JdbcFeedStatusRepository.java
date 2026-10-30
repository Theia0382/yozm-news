package net.yozmnews.source.adapter.out.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.domain.poll.FeedStatus;
import net.yozmnews.source.domain.poll.FeedStatusRepository;
import net.yozmnews.source.domain.poll.FeedStatusTarget;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link FeedStatusRepository} 의 JdbcClient 구현 — docs/specs/source.md §4.
 * <p>
 * 열 목록과 행 읽기는 {@link JdbcFeedPollStateRepository} 의 것을 쓴다. 대상 행의 잠금은 건너뛰지 않고 기다린다.
 */
@Repository
class JdbcFeedStatusRepository implements FeedStatusRepository {

	private final JdbcClient jdbc;

	JdbcFeedStatusRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<FeedStatusTarget> lockTargets() {
		return this.jdbc.sql("select " + JdbcFeedPollStateRepository.COLUMNS + """
				, ps.status
				from feed_poll_states ps
				join feeds f on f.id = ps.feed_id
				join sources s on s.id = f.source_id
				where s.enabled and f.enabled
				order by ps.feed_id
				for update of ps
				""")
			.query((rs, row) -> new FeedStatusTarget(JdbcFeedPollStateRepository.state(rs),
					FeedStatus.valueOf(rs.getString("status"))))
			.list();
	}

	@Override
	public void saveStatus(FeedId feedId, FeedStatus status, Instant changedAt) {
		this.jdbc.sql("""
				update feed_poll_states set status = :status, status_changed_at = :changedAt where feed_id = :feedId
				""")
			.param("status", status.name())
			.param("changedAt", changedAt.atOffset(ZoneOffset.UTC))
			.param("feedId", feedId.value())
			.update();
	}

}
