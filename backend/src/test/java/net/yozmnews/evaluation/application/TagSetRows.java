package net.yozmnews.evaluation.application;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 픽스처 — 같은 DB 를 쓰는 시험끼리 태그 기준 · 저장 기준 · 이력이 서로를 흔들지 않게 체계 상태를 처음 모양으로 되돌린다.
 * <p>
 * 이력을 지우는 것은 이 픽스처뿐이다 — 프로덕션 코드에는 그 경로가 없다(evaluation-R13).
 */
final class TagSetRows {

	private TagSetRows() {
	}

	/** 활성 버전의 태그 기준 · 저장 기준을 처음 값(0.5 / 0.1)으로, 이력은 태그 종류마다 시스템 첫 행만 남긴다 */
	static void resetThresholds(JdbcClient jdbc) {
		jdbc.sql("""
				update tag_set_thresholds set tag_threshold = 0.5, store_threshold = 0.1
				where tag_set_id = (select id from tag_sets where active)
				""").update();
		jdbc.sql("""
				delete from tag_set_threshold_changes
				where tag_set_id = (select id from tag_sets where active) and actor <> 'SYSTEM'
				""").update();
	}

	/** 체계 행을 모두 지운다 — 적재 시험이 빈 표에서 시작하도록. 끝에는 다시 적재해 처음 모양으로 둔다 */
	static void removeAll(JdbcClient jdbc) {
		jdbc.sql("delete from tag_set_threshold_changes").update();
		jdbc.sql("delete from tag_set_thresholds").update();
		jdbc.sql("delete from importance_rubrics").update();
		jdbc.sql("delete from tags").update();
		jdbc.sql("delete from tag_sets").update();
	}

	static long count(JdbcClient jdbc, String table) {
		return jdbc.sql("select count(*) from " + table).query(Long.class).single();
	}

}
