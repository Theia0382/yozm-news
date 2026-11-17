package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.storyline.StorylineLinkingCriteria;
import org.jspecify.annotations.Nullable;

/**
 * 잇기 기준 — 시간 창(초) · 아래 기준 · 위 기준 · 확률 기준 · docs/specs/admin.md §5 admin-R7.
 *
 * @param lower 아래 기준 — 위 기준과 같으면 띠가 없다(점수만으로 정한다)
 * @param upper 위 기준 — 이 이상이면 묻지 않고 붙인다
 * @param probability 확률 기준 — 띠가 생기기 전의 기준 이력에는 없다(null)
 */
record LinkingCriteriaResponse(long windowSeconds, double lower, double upper, @Nullable Double probability) {

	static LinkingCriteriaResponse of(StorylineLinkingCriteria criteria) {
		return new LinkingCriteriaResponse(criteria.window().toSeconds(), criteria.lower(), criteria.upper(),
				criteria.probability());
	}

}
