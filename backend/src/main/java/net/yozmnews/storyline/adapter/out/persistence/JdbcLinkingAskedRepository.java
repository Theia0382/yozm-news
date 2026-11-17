package net.yozmnews.storyline.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import net.yozmnews.storyline.domain.linking.Asked;
import net.yozmnews.storyline.domain.linking.AskedRecord;
import net.yozmnews.storyline.domain.linking.LinkingAskedRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link LinkingAskedRepository} 의 JdbcClient 구현 — docs/specs/storyline.md §4.
 * <p>
 * 붙이기 띠는 {@code storyline_link_asked}(스토리라인 FK — 지우면 함께 지워진다), 짝 띠는 {@code storyline_pair_asked} 다. 두 표는
 * 상대 칸의 이름(스토리라인 · 이웃 사건)과 크기 칸(구성 사건 수 · 기사 수)만 다르다 — 표와 칸 이름은 이 클래스의 상수만 넘긴다. 쓰기는
 * 상대 ID 순 {@code INSERT … ON CONFLICT DO UPDATE} 로 덮어쓴다.
 */
@Repository
class JdbcLinkingAskedRepository implements LinkingAskedRepository {

	private static final Table LINK = new Table("storyline_link_asked", "storyline_id", "storyline_events");

	private static final Table PAIR = new Table("storyline_pair_asked", "partner_id", "partner_articles");

	private final JdbcClient jdbc;

	JdbcLinkingAskedRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<Long, AskedRecord> ofLink(long eventId, Collection<Long> storylineIds) {
		return read(LINK, eventId, storylineIds);
	}

	@Override
	public Map<Long, AskedRecord> ofPair(long eventId, Collection<Long> partnerIds) {
		return read(PAIR, eventId, partnerIds);
	}

	@Override
	public void recordLink(long eventId, Map<Long, AskedRecord> records, Instant now) {
		write(LINK, eventId, records, now);
	}

	@Override
	public void recordPair(long eventId, Map<Long, AskedRecord> records, Instant now) {
		write(PAIR, eventId, records, now);
	}

	@Override
	public void forgetStoryline(long storylineId) {
		this.jdbc.sql("delete from storyline_link_asked where storyline_id = :storyline")
			.param("storyline", storylineId)
			.update();
	}

	@Override
	public void forgetEvent(long eventId) {
		this.jdbc.sql("delete from storyline_link_asked where event_id = :event").param("event", eventId).update();
		this.jdbc.sql("delete from storyline_pair_asked where event_id = :event or partner_id = :event")
			.param("event", eventId)
			.update();
	}

	private Map<Long, AskedRecord> read(Table table, long eventId, Collection<Long> others) {
		if (others.isEmpty()) {
			return Map.of();
		}
		Map<Long, AskedRecord> found = new LinkedHashMap<>();
		this.jdbc.sql("""
				select %s as other_id, probability, judgement_id, event_articles, %s as other_size
				from %s where event_id = :event and %s = any(:others)
				order by %s
				""".formatted(table.other(), table.size(), table.name(), table.other(), table.other()))
			.param("event", eventId)
			.param("others", others.toArray(Long[]::new))
			.query((ResultSet rs) -> {
				found.put(rs.getLong("other_id"),
						new AskedRecord(asked(rs), rs.getInt("event_articles"), rs.getInt("other_size")));
			});
		return Collections.unmodifiableMap(found);
	}

	private void write(Table table, long eventId, Map<Long, AskedRecord> records, Instant now) {
		new TreeMap<>(records).forEach((other, record) -> this.jdbc.sql("""
				insert into %s (event_id, %s, probability, judgement_id, event_articles, %s, asked_at)
				values (:event, :other, :probability, :judgement, :eventArticles, :otherSize, :now)
				on conflict (event_id, %s) do update
				set probability = excluded.probability, judgement_id = excluded.judgement_id,
				    event_articles = excluded.event_articles, %s = excluded.%s, asked_at = excluded.asked_at
				""".formatted(table.name(), table.other(), table.size(), table.other(), table.size(), table.size()))
			.param("event", eventId)
			.param("other", other)
			.param("probability", record.asked().probability(), Types.DOUBLE)
			.param("judgement", record.asked().judgementId(), Types.BIGINT)
			.param("eventArticles", record.eventArticles())
			.param("otherSize", record.otherSize())
			.param("now", now.atOffset(ZoneOffset.UTC))
			.update());
	}

	private static Asked asked(ResultSet rs) throws SQLException {
		double probability = rs.getDouble("probability");
		if (rs.wasNull()) {
			return Asked.UNANSWERED;
		}
		return new Asked(probability, rs.getLong("judgement_id"));
	}

	/** 물은 기록 표 하나 — 표 이름, 상대 칸, 상대 크기 칸 */
	private record Table(String name, String other, String size) {
	}

}
