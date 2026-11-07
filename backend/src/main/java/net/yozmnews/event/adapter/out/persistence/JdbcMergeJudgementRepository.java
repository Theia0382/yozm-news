package net.yozmnews.event.adapter.out.persistence;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.yozmnews.event.domain.assignment.MergeJudgementRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link MergeJudgementRepository} 의 JdbcClient 구현 — docs/specs/event.md §4.
 * <p>
 * 쌍은 (작은 ID, 큰 ID) 한 행이다. 넣기는 {@code INSERT … ON CONFLICT DO NOTHING} 이라 먼저 남긴 확률이 남는다.
 */
@Repository
class JdbcMergeJudgementRepository implements MergeJudgementRepository {

	private final JdbcClient jdbc;

	JdbcMergeJudgementRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<Long, Double> find(long eventId, Collection<Long> others) {
		Map<Long, Double> found = new HashMap<>();
		if (others.isEmpty()) {
			return found;
		}
		this.jdbc.sql("""
				select case when event_low = :event then event_high else event_low end as other, probability
				from event_merge_judgements
				where (event_low = :event and event_high = any(:others)) or (event_high = :event and event_low = any(:others))
				""").param("event", eventId).param("others", others.toArray(Long[]::new)).query((ResultSet rs) -> {
					found.put(rs.getLong("other"), rs.getDouble("probability"));
				});
		return found;
	}

	@Override
	public void save(long eventId, Map<Long, Double> probabilities, Instant now) {
		probabilities.forEach((other, probability) -> this.jdbc.sql("""
				insert into event_merge_judgements (event_low, event_high, probability, judged_at)
				values (:low, :high, :probability, :now)
				on conflict (event_low, event_high) do nothing
				""")
			.param("low", Math.min(eventId, other))
			.param("high", Math.max(eventId, other))
			.param("probability", probability)
			.param("now", now.atOffset(ZoneOffset.UTC))
			.update());
	}

}
