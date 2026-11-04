package net.yozmnews.evaluation.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagThresholds;
import net.yozmnews.evaluation.ThresholdChangeView;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdValues;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link ThresholdRepository} 의 JdbcClient 구현 — docs/specs/evaluation.md §4.
 * <p>
 * 잠금은 {@code SELECT … FOR UPDATE}, 갱신과 이력 추가는 같은 트랜잭션의 두 문장이다(evaluation-R11 · R13). 태그 종류 · 행위자는 상수
 * 이름으로 쓴다.
 */
@Repository
class JdbcThresholdRepository implements ThresholdRepository {

	private final JdbcClient jdbc;

	JdbcThresholdRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void insertInitial(TagSetId version, MultiTagKind kind, ThresholdValues values, ChangeActor actor,
			Instant at) {
		this.jdbc.sql("""
				insert into tag_set_thresholds (tag_set_id, tag_kind, tag_threshold, store_threshold)
				values (:version, :kind, :tag, :store)
				""")
			.param("version", version.value())
			.param("kind", kind.name())
			.param("tag", values.tagThreshold())
			.param("store", values.storeThreshold())
			.update();
		appendChange(version, kind, null, values, actor, at);
	}

	@Override
	public Optional<ThresholdValues> lockCurrent(TagSetId version, MultiTagKind kind) {
		return this.jdbc.sql("""
				select tag_threshold, store_threshold from tag_set_thresholds
				where tag_set_id = :version and tag_kind = :kind
				for update
				""")
			.param("version", version.value())
			.param("kind", kind.name())
			.query((rs, row) -> new ThresholdValues(rs.getBigDecimal("tag_threshold"), rs.getBigDecimal("store_threshold")))
			.optional();
	}

	@Override
	public void apply(TagSetId version, MultiTagKind kind, ThresholdValues previous, ThresholdValues next,
			ChangeActor actor, Instant at) {
		int updated = this.jdbc.sql("""
				update tag_set_thresholds set tag_threshold = :tag, store_threshold = :store
				where tag_set_id = :version and tag_kind = :kind
				""")
			.param("tag", next.tagThreshold())
			.param("store", next.storeThreshold())
			.param("version", version.value())
			.param("kind", kind.name())
			.update();
		if (updated != 1) {
			throw new IllegalStateException("태그 기준 · 저장 기준 행이 없다: " + version + " " + kind);
		}
		appendChange(version, kind, previous, next, actor, at);
	}

	@Override
	public List<ThresholdChangeView> changes(TagSetId version) {
		return this.jdbc.sql("""
				select tag_kind, previous_tag_threshold, previous_store_threshold, tag_threshold, store_threshold,
				       actor, changed_at
				from tag_set_threshold_changes where tag_set_id = :version
				order by id desc
				""")
			.param("version", version.value())
			.query((rs, row) -> new ThresholdChangeView(MultiTagKind.valueOf(rs.getString("tag_kind")),
					rs.getBigDecimal("previous_tag_threshold"), rs.getBigDecimal("previous_store_threshold"),
					rs.getBigDecimal("tag_threshold"), rs.getBigDecimal("store_threshold"),
					ChangeActor.valueOf(rs.getString("actor")),
					rs.getObject("changed_at", OffsetDateTime.class).toInstant()))
			.list();
	}

	@Override
	public TagSetThresholds current(TagSetId version) {
		Map<MultiTagKind, TagThresholds> rows = new EnumMap<>(MultiTagKind.class);
		this.jdbc.sql("""
				select tag_kind, tag_threshold, store_threshold from tag_set_thresholds where tag_set_id = :version
				""")
			.param("version", version.value())
			.query(rs -> {
				MultiTagKind kind = MultiTagKind.valueOf(rs.getString("tag_kind"));
				rows.put(kind, new TagThresholds(kind, rs.getBigDecimal("tag_threshold"),
						rs.getBigDecimal("store_threshold")));
			});
		TagThresholds topic = rows.get(MultiTagKind.TOPIC);
		TagThresholds region = rows.get(MultiTagKind.REGION);
		if (topic == null || region == null) {
			throw new IllegalStateException("태그 종류마다 태그 기준 · 저장 기준 행이 있어야 한다: " + version + " " + rows.keySet());
		}
		return new TagSetThresholds(version, topic, region);
	}

	private void appendChange(TagSetId version, MultiTagKind kind, @Nullable ThresholdValues previous, ThresholdValues next,
			ChangeActor actor, Instant at) {
		this.jdbc.sql("""
				insert into tag_set_threshold_changes (tag_set_id, tag_kind, previous_tag_threshold,
				    previous_store_threshold, tag_threshold, store_threshold, actor, changed_at)
				values (:version, :kind, :previousTag, :previousStore, :tag, :store, :actor, :at)
				""")
			.param("version", version.value())
			.param("kind", kind.name())
			.param("previousTag", (previous != null) ? previous.tagThreshold() : null)
			.param("previousStore", (previous != null) ? previous.storeThreshold() : null)
			.param("tag", next.tagThreshold())
			.param("store", next.storeThreshold())
			.param("actor", actor.name())
			.param("at", at.atOffset(ZoneOffset.UTC))
			.update();
	}

}
