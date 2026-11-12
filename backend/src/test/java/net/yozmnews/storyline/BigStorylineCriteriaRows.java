package net.yozmnews.storyline;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 도우미 — 큰 판정 기준의 지금 값과 이력을 비운다. 기준은 인스턴스에 한 행이라 시험 클래스 사이에 공유된다 — 기준을 바꾸는 시험은
 * 시작과 끝에 비우고 처음 값을 다시 적재한다.
 */
public final class BigStorylineCriteriaRows {

	private BigStorylineCriteriaRows() {
	}

	/** 지금 값과 이력을 모두 지운다 */
	public static void removeAll(JdbcClient jdbc) {
		jdbc.sql("delete from storyline_big_criteria_changes").update();
		jdbc.sql("delete from storyline_big_criteria").update();
	}

}
