package net.yozmnews.source.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.poll.SlotLock;
import net.yozmnews.source.domain.poll.SourceFetchSlotRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link SourceFetchSlotRepository} 의 JdbcClient 구현 — docs/specs/source.md §4.
 * <p>
 * 행은 소스마다 하나이고 처음 예약할 때 만든다 — 잠금은 그 행에 건다.
 */
@Repository
class JdbcSourceFetchSlotRepository implements SourceFetchSlotRepository {

	private final JdbcClient jdbc;

	JdbcSourceFetchSlotRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public SlotLock lockNextFetchAt(SourceId source) {
		// 소스 행을 먼저 공유 잠금한다 — 삭제의 for update 와 줄을 서고, 이미 지워졌으면 예약 행을 만들지 않는다
		if (this.jdbc.sql("select id from sources where id = :source for key share")
			.param("source", source.value())
			.query(Long.class)
			.optional()
			.isEmpty()) {
			return new SlotLock.SourceGone();
		}
		this.jdbc.sql("""
				insert into source_fetch_slots (source_id, next_fetch_at) values (:source, null)
				on conflict (source_id) do nothing
				""").param("source", source.value()).update();
		OffsetDateTime next = this.jdbc
			.sql("select next_fetch_at from source_fetch_slots where source_id = :source for update")
			.param("source", source.value())
			.query((rs, row) -> rs.getObject("next_fetch_at", OffsetDateTime.class))
			.list()
			.getFirst();
		return new SlotLock.Locked((next != null) ? next.toInstant() : null);
	}

	@Override
	public void deleteBySource(SourceId source) {
		this.jdbc.sql("delete from source_fetch_slots where source_id = :source").param("source", source.value()).update();
	}

	@Override
	public void saveNextFetchAt(SourceId source, Instant next) {
		this.jdbc.sql("update source_fetch_slots set next_fetch_at = :next where source_id = :source")
			.param("next", next.atOffset(ZoneOffset.UTC))
			.param("source", source.value())
			.update();
	}

}
