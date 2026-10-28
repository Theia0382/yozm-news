package net.yozmnews.source.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.poll.SourceFetchSlotRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code source_fetch_slots} 를 JdbcClient 로 읽고 쓴다. 행은 소스마다 하나이고 처음 예약할 때 만든다 — 잠금은 그 행에 건다.
 */
@Repository
class JdbcSourceFetchSlotRepository implements SourceFetchSlotRepository {

	private final JdbcClient jdbc;

	JdbcSourceFetchSlotRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public @Nullable Instant lockNextFetchAt(SourceId source) {
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
		return (next != null) ? next.toInstant() : null;
	}

	@Override
	public void saveNextFetchAt(SourceId source, Instant next) {
		this.jdbc.sql("update source_fetch_slots set next_fetch_at = :next where source_id = :source")
			.param("next", next.atOffset(ZoneOffset.UTC))
			.param("source", source.value())
			.update();
	}

}
