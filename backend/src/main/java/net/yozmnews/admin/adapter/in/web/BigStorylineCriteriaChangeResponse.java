package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.storyline.BigStorylineCriteriaChangeResult;
import org.jspecify.annotations.Nullable;

/**
 * 큰 판정 기준 변경의 결과 — docs/specs/admin.md §3 · admin-R1 · storyline-R20.
 * <p>
 * {@code CHANGED} 면 이전 값과 새 값, {@code UNCHANGED} 면 지금 값이 있다. 범위 밖 값은 결과가 아니라 400 이다.
 */
record BigStorylineCriteriaChangeResponse(Outcome result, @Nullable BigStorylineCriteriaResponse previous,
		BigStorylineCriteriaResponse current) {

	/**
	 * 결과를 응답으로 옮긴다.
	 * @throws org.springframework.web.server.ResponseStatusException 400 — 값이 범위 밖일 때
	 */
	static BigStorylineCriteriaChangeResponse of(BigStorylineCriteriaChangeResult result) {
		return switch (result) {
			case BigStorylineCriteriaChangeResult.Changed changed -> new BigStorylineCriteriaChangeResponse(Outcome.CHANGED,
					BigStorylineCriteriaResponse.of(changed.previous()), BigStorylineCriteriaResponse.of(changed.current()));
			case BigStorylineCriteriaChangeResult.Unchanged unchanged -> new BigStorylineCriteriaChangeResponse(
					Outcome.UNCHANGED, null, BigStorylineCriteriaResponse.of(unchanged.current()));
			case BigStorylineCriteriaChangeResult.OutOfRange outOfRange -> throw BigStorylineCriteriaProblem.outOfRange();
		};
	}

	enum Outcome {

		/** 바꿨다 */
		CHANGED,
		/** 지금 값과 같아 바꾸지 않았다 */
		UNCHANGED

	}

}
