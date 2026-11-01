package net.yozmnews.source.adapter.out.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.attribution.SourceAttributionRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link SourceAttributionRepository} 의 JdbcClient 구현 — docs/specs/source.md §4.
 * <p>
 * 이름이 다를 때만 쓰는 upsert 하나로 기록한다 — 운영 설정 행 · 버전과 무관하다.
 */
@Repository
class JdbcSourceAttributionRepository implements SourceAttributionRepository {

	private final JdbcClient jdbc;

	JdbcSourceAttributionRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void record(SourceId source, String name, Instant at) {
		this.jdbc.sql("""
				insert into source_attributions (source_id, name, recorded_at) values (:source, :name, :at)
				on conflict (source_id) do update set name = excluded.name, recorded_at = excluded.recorded_at
				where source_attributions.name is distinct from excluded.name
				""")
			.param("source", source.value())
			.param("name", name)
			.param("at", at.atOffset(ZoneOffset.UTC))
			.update();
	}

	@Override
	public Map<SourceId, String> findAll() {
		Map<SourceId, String> names = new LinkedHashMap<>();
		this.jdbc.sql("select source_id, name from source_attributions order by source_id")
			.query(rs -> {
				names.put(new SourceId(rs.getLong("source_id")), rs.getString("name"));
			});
		return names;
	}

	@Override
	public void delete(SourceId source) {
		this.jdbc.sql("delete from source_attributions where source_id = :source")
			.param("source", source.value())
			.update();
	}

}
