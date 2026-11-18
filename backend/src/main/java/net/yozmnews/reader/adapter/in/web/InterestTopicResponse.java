package net.yozmnews.reader.adapter.in.web;

import java.util.List;

/**
 * 관심사 히스토리의 주제 중분류 하나 — docs/specs/reader.md §5
 *
 * @param key 중분류 전체 키
 * @param name 중분류 이름
 * @param categoryName 대분류 이름
 * @param levels 기간마다 관심 — 기간 순
 * @param prominent 보인 기간 가운데 한 번이라도 관심이 드러났다 — 아니면 화면이 "그 밖의 주제"로 접는다
 */
record InterestTopicResponse(String key, String name, String categoryName, List<InterestLevelResponse> levels,
		boolean prominent) {
}
