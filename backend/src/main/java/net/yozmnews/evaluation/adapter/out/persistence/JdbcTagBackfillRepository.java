package net.yozmnews.evaluation.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagBackfillRunView;
import net.yozmnews.evaluation.TagBackfillVersionView;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.backfill.BackfillCounts;
import net.yozmnews.evaluation.domain.backfill.BackfillVersionPlan;
import net.yozmnews.evaluation.domain.backfill.TagBackfill;
import net.yozmnews.evaluation.domain.backfill.TagBackfillRepository;
import net.yozmnews.evaluation.domain.threshold.StoreCoverage;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link TagBackfillRepository} 의 JdbcClient 구현 — docs/specs/evaluation.md §4.
 * <p>
 * 넣기는 끝나지 않은 행 하나의 부분 고유 인덱스에 {@code INSERT … ON CONFLICT DO NOTHING} 으로 판정한다 — 위반 예외는 트랜잭션을 깨므로
 * 쓰지 않는다. 잠금은 {@code FOR UPDATE SKIP LOCKED}, 점유한 회차의 쓰기는 점유 토큰을 조건으로 한 갱신이다.
 */
@Repository
class JdbcTagBackfillRepository implements TagBackfillRepository {

	private static final String BACKFILL_COLUMNS = """
			id, days, covered_from, covered_to, eligible_at, cursor_id, lease_token, lease_expires_at, started_at,
			finished_at""";

	private final JdbcClient jdbc;

	JdbcTagBackfillRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Long> insertIfNoneOpen(int days, Instant from, Instant to, Instant eligibleAt,
			List<BackfillVersionPlan> versions) {
		Optional<Long> id = this.jdbc.sql("""
				insert into tag_backfills (days, covered_from, covered_to, eligible_at)
				values (:days, :from, :to, :eligibleAt)
				on conflict do nothing
				returning id
				""")
			.param("days", days)
			.param("from", timestamp(from))
			.param("to", timestamp(to))
			.param("eligibleAt", timestamp(eligibleAt))
			.query(Long.class)
			.optional();
		id.ifPresent(backfill -> {
			for (BackfillVersionPlan plan : versions) {
				this.jdbc.sql("""
						insert into tag_backfill_versions (backfill_id, tag_set_id, topic_store_threshold,
						    region_store_threshold, evaluations)
						values (:backfill, :version, :topic, :region, :evaluations)
						""")
					.param("backfill", backfill)
					.param("version", plan.version().value())
					.param("topic", plan.topicStoreThreshold())
					.param("region", plan.regionStoreThreshold())
					.param("evaluations", plan.evaluations())
					.update();
			}
		});
		return id;
	}

	@Override
	public Optional<TagBackfill> findOpen() {
		return this.jdbc.sql("select " + BACKFILL_COLUMNS + " from tag_backfills where finished_at is null")
			.query(JdbcTagBackfillRepository::backfill)
			.optional();
	}

	@Override
	public Optional<TagBackfill> lockDue(Instant now) {
		return this.jdbc.sql("select " + BACKFILL_COLUMNS + """
				 from tag_backfills
				where finished_at is null and eligible_at <= :now
				  and (lease_token is null or lease_expires_at <= :now)
				for update skip locked
				""")
			.param("now", timestamp(now))
			.query(JdbcTagBackfillRepository::backfill)
			.optional();
	}

	@Override
	public void saveLocked(TagBackfill claimed) {
		int updated = this.jdbc.sql("""
				update tag_backfills
				set lease_token = :token, lease_expires_at = :expires, started_at = :started
				where id = :id
				""")
			.param("token", claimed.leaseToken())
			.param("expires", nullableTimestamp(claimed.leaseExpiresAt()))
			.param("started", nullableTimestamp(claimed.startedAt()))
			.param("id", claimed.id())
			.update();
		if (updated != 1) {
			throw new IllegalStateException("잠근 채움 행이 없다: " + claimed.id());
		}
	}

	@Override
	public boolean advance(long id, UUID token, long cursor, Map<TagSetId, BackfillCounts> counts,
			Instant leaseExpiresAt) {
		int updated = this.jdbc.sql("""
				update tag_backfills set cursor_id = :cursor, lease_expires_at = :expires
				where id = :id and lease_token = :token
				""")
			.param("cursor", cursor)
			.param("expires", timestamp(leaseExpiresAt))
			.param("id", id)
			.param("token", token)
			.update();
		if (updated != 1) {
			return false;
		}
		counts.forEach((version, add) -> this.jdbc.sql("""
				update tag_backfill_versions
				set processed = processed + :processed, tags_added = tags_added + :added,
				    skipped_other = skipped_other + :other, skipped_unreadable = skipped_unreadable + :unreadable
				where backfill_id = :id and tag_set_id = :version
				""")
			.param("processed", add.processed())
			.param("added", add.tagsAdded())
			.param("other", add.skippedOther())
			.param("unreadable", add.skippedUnreadable())
			.param("id", id)
			.param("version", version.value())
			.update());
		return true;
	}

	@Override
	public boolean finish(long id, UUID token, Instant now) {
		return this.jdbc.sql("""
				update tag_backfills set finished_at = :now, lease_token = null, lease_expires_at = null
				where id = :id and lease_token = :token
				""").param("now", timestamp(now)).param("id", id).param("token", token).update() == 1;
	}

	@Override
	public boolean release(long id, UUID token) {
		return this.jdbc.sql("""
				update tag_backfills set lease_token = null, lease_expires_at = null
				where id = :id and lease_token = :token
				""").param("id", id).param("token", token).update() == 1;
	}

	@Override
	public Map<TagSetId, BackfillVersionPlan> versions(long id) {
		Map<TagSetId, BackfillVersionPlan> plans = new LinkedHashMap<>();
		this.jdbc.sql("""
				select tag_set_id, topic_store_threshold, region_store_threshold, evaluations
				from tag_backfill_versions where backfill_id = :id
				order by tag_set_id
				""").param("id", id).query(rs -> {
			TagSetId version = new TagSetId(rs.getLong("tag_set_id"));
			plans.put(version, new BackfillVersionPlan(version, rs.getBigDecimal("topic_store_threshold"),
					rs.getBigDecimal("region_store_threshold"), rs.getLong("evaluations")));
		});
		return plans;
	}

	@Override
	public Optional<TagBackfillRunView> run(long id) {
		return this.jdbc.sql("select " + BACKFILL_COLUMNS + " from tag_backfills where id = :id")
			.param("id", id)
			.query(JdbcTagBackfillRepository::backfill)
			.optional()
			.map(this::view);
	}

	@Override
	public List<TagBackfillRunView> runs() {
		return this.jdbc.sql("select " + BACKFILL_COLUMNS + " from tag_backfills order by id desc")
			.query(JdbcTagBackfillRepository::backfill)
			.list()
			.stream()
			.map(this::view)
			.toList();
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
			.query((rs, row) -> new StoreCoverage(instant(rs, "covered_from"), instant(rs, "covered_to"),
					rs.getBigDecimal("store_threshold")))
			.list();
	}

	/** 채움 행과 버전별 행 — 버전 번호 순, 합은 버전별 행에서 더한다 */
	private TagBackfillRunView view(TagBackfill backfill) {
		List<TagBackfillVersionView> versions = new ArrayList<>();
		this.jdbc.sql("""
				select v.tag_set_id, s.number, v.topic_store_threshold, v.region_store_threshold, v.evaluations,
				       v.processed, v.tags_added, v.skipped_other, v.skipped_unreadable
				from tag_backfill_versions v join tag_sets s on s.id = v.tag_set_id
				where v.backfill_id = :id
				order by s.number
				""").param("id", backfill.id()).query(rs -> {
			versions.add(new TagBackfillVersionView(new TagSetId(rs.getLong("tag_set_id")), rs.getInt("number"),
					rs.getBigDecimal("topic_store_threshold"), rs.getBigDecimal("region_store_threshold"),
					rs.getLong("evaluations"), rs.getLong("processed"), rs.getLong("tags_added"),
					rs.getLong("skipped_other"), rs.getLong("skipped_unreadable")));
		});
		return new TagBackfillRunView(backfill.id(), backfill.days(), backfill.from(), backfill.to(), backfill.to(),
				backfill.state(), backfill.startedAt(), backfill.finishedAt(),
				versions.stream().mapToLong(TagBackfillVersionView::evaluations).sum(),
				versions.stream().mapToLong(TagBackfillVersionView::processed).sum(),
				versions.stream().mapToLong(TagBackfillVersionView::tagsAdded).sum(),
				versions.stream().mapToLong(TagBackfillVersionView::skippedOtherEvaluator).sum(),
				versions.stream().mapToLong(TagBackfillVersionView::skippedUnreadable).sum(), versions);
	}

	private static TagBackfill backfill(ResultSet rs, int row) throws SQLException {
		return new TagBackfill(rs.getLong("id"), rs.getInt("days"), instant(rs, "covered_from"),
				instant(rs, "covered_to"), instant(rs, "eligible_at"), rs.getLong("cursor_id"),
				rs.getObject("lease_token", UUID.class), nullableInstant(rs, "lease_expires_at"),
				nullableInstant(rs, "started_at"), nullableInstant(rs, "finished_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

	private static @Nullable Instant nullableInstant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return (value != null) ? value.toInstant() : null;
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static @Nullable OffsetDateTime nullableTimestamp(@Nullable Instant instant) {
		return (instant != null) ? timestamp(instant) : null;
	}

}
