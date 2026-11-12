package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.evaluation.ImportanceScale;
import net.yozmnews.storyline.BigStorylineCriteria;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 범위 밖 큰 판정 기준의 400 — 범위는 공개 타입 {@link BigStorylineCriteria} · 공개 API 가 판정하고, 설명의 수는 {@link ImportanceScale} 과
 * {@link BigStorylineCriteria#IMPORTANCE_SCALE} 에서 읽는다(admin-R3).
 */
final class BigStorylineCriteriaProblem {

	private BigStorylineCriteriaProblem() {
	}

	static ResponseStatusException outOfRange() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"큰 판정 기준은 소스 1 이상, 중요도 " + ImportanceScale.MIN_LEVEL + " ~ " + ImportanceScale.MAX_LEVEL + " 의 소수 "
						+ BigStorylineCriteria.IMPORTANCE_SCALE + "자리까지다");
	}

}
