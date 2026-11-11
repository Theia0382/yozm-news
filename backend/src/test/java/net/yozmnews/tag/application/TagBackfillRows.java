package net.yozmnews.tag.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import net.yozmnews.tag.TagSetId;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 픽스처 — 끝난 채움 행을 SQL 로 넣고 지운다. 저장 바닥 계산의 입력(덮음)을 주는 것이지 판정 결과를 주는 것이 아니다 — 경고 · 표시는
 * 생산 경로가 낸다.
 * <p>
 * 채움 행을 지우는 것은 이 픽스처와 버전을 지우는 {@link TagSetRows} 뿐이다 — 프로덕션 코드에는 그 경로가 없다.
 */
public final class TagBackfillRows {

	private TagBackfillRows() {
	}

	/** 그 버전 하나를 {@code [from, to)} 동안 주제 · 지역 같은 저장 기준으로 덮은 끝난 채움을 넣는다 — 건너뛴 평가는 skipped 개 */
	static void insertFinished(JdbcClient jdbc, TagSetId version, Instant from, Instant to, String storeThreshold,
			long skipped) {
		long id = jdbc.sql("""
				insert into tag_backfills (days, covered_from, covered_to, eligible_at, started_at, finished_at)
				values (1, :from, :to, :to, :to, :to)
				returning id
				""")
			.param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
			.param("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
			.query(Long.class)
			.single();
		jdbc.sql("""
				insert into tag_backfill_versions (backfill_id, tag_set_id, topic_store_threshold, region_store_threshold,
				    evaluations, processed, skipped_unreadable)
				values (:id, :version, :store, :store, :skipped, :skipped, :skipped)
				""")
			.param("id", id)
			.param("version", version.value())
			.param("store", new BigDecimal(storeThreshold))
			.param("skipped", skipped)
			.update();
	}

	/** 채움 행을 모두 지운다 */
	public static void removeAll(JdbcClient jdbc) {
		jdbc.sql("delete from tag_backfill_versions").update();
		jdbc.sql("delete from tag_backfills").update();
	}

}
