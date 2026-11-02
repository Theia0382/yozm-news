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
		jdbc.sql("delete from tag_key_lineage").update();
		jdbc.sql("delete from tag_set_threshold_changes").update();
		jdbc.sql("delete from tag_set_thresholds").update();
		jdbc.sql("delete from importance_rubrics").update();
		jdbc.sql("delete from tags").update();
		jdbc.sql("delete from tag_sets").update();
	}

	/**
	 * 버전 2 이상과 그 행(계보 · 태그 기준 · 저장 기준 · 이력 · 태그 · 중요도 기준 · 평가)을 지우고 버전 1 을 다시 켠다 — 버전을 만든 시험의
	 * 뒤처리. 버전을 지우는 것은 이 픽스처뿐이다 — 프로덕션 코드에는 그 경로가 없다
	 */
	static void removeVersionsAfterFirst(JdbcClient jdbc) {
		String later = "(select id from tag_sets where number > 1)";
		jdbc.sql("delete from article_tags where evaluation_id in (select id from article_evaluations where tag_set_id in "
				+ later + ")").update();
		jdbc.sql("delete from article_evaluations where tag_set_id in " + later).update();
		jdbc.sql("delete from tag_key_lineage where tag_set_id in " + later).update();
		jdbc.sql("delete from tag_set_threshold_changes where tag_set_id in " + later).update();
		jdbc.sql("delete from tag_set_thresholds where tag_set_id in " + later).update();
		jdbc.sql("delete from importance_rubrics where tag_set_id in " + later).update();
		jdbc.sql("delete from tags where tag_set_id in " + later).update();
		jdbc.sql("delete from tag_sets where number > 1").update();
		jdbc.sql("update tag_sets set active = true where number = 1").update();
	}

	static long count(JdbcClient jdbc, String table) {
		return jdbc.sql("select count(*) from " + table).query(Long.class).single();
	}

}
