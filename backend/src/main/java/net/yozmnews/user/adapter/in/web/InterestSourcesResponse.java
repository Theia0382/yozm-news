package net.yozmnews.user.adapter.in.web;

import java.util.List;

import net.yozmnews.user.application.ExcludedSources.ExcludedSourcesView;
import org.jspecify.annotations.Nullable;

/**
 * 제외 소스 조회 — 고를 소스(숨기지 않은 소스, 소스 ID 순)와 그 가운데 제외한 것. docs/specs/user.md §3 · user-R49
 *
 * @param sources 고를 소스
 * @param excluded 제외한 소스 ID — 오름차순
 */
record InterestSourcesResponse(List<InterestSourceResponse> sources, List<Long> excluded) {

	static InterestSourcesResponse of(ExcludedSourcesView view) {
		return new InterestSourcesResponse(
				view.sources().stream().map(source -> new InterestSourceResponse(source.id().value(), source.name())).toList(),
				view.excluded().stream().map(id -> id.value()).sorted().toList());
	}

	/**
	 * 소스 하나 — 명세의 스키마 이름은 단순 클래스 이름이라 운영자 API 의 소스 응답과 겹치지 않게 Interest 를 앞에 둔다.
	 *
	 * @param id 소스 ID
	 * @param name 출처 이름 — 없으면 null
	 */
	record InterestSourceResponse(long id, @Nullable String name) {
	}

}
