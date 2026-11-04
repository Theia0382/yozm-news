package net.yozmnews.evaluation.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.util.List;

import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.backfill.TagBackfillRepository;
import net.yozmnews.evaluation.domain.threshold.StoreCoverage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link TagBackfillRepository} 의 JdbcClient 구현 — docs/specs/evaluation.md §4.
 */
@Repository
class JdbcTagBackfillRepository implements TagBackfillRepository {

	private final JdbcClient jdbc;

	JdbcTagBackfillRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<StoreCoverage> coverages(TagSetId version, MultiTagKind kind) {
		String column = switch (kind) {
			case TOPIC -> "topic_store_threshold";
			case REGION -> "region_store_threshold";
		};
		return this.jdbc.sql("""
				select b.covered_from, b.covered_to, v.%s as store_threshold
				from tag_backfills b join tag_backfill_versions v on v.backfill_id = b.id
				where v.tag_set_id = :version and b.finished_at is not null
				  and v.skipped_other = 0 and v.skipped_unreadable = 0
				order by b.id
				""".formatted(column))
			.param("version", version.value())
			.query((rs, row) -> new StoreCoverage(rs.getObject("covered_from", OffsetDateTime.class).toInstant(),
					rs.getObject("covered_to", OffsetDateTime.class).toInstant(), rs.getBigDecimal("store_threshold")))
			.list();
	}

}
