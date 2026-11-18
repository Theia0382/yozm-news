package net.yozmnews.reader.adapter.in.web;

import java.util.List;

import net.yozmnews.digest.InterestHistorySpan;

/**
 * 관심사 히스토리 — 고른 기간 · 고를 수 있는 기간 · 기간마다 변화 · 주제마다 관심. docs/specs/reader.md §5 · reader-R8
 *
 * @param span 보인 기간
 * @param spans 고를 수 있는 기간 — 단위마다 하나, 단위 순
 * @param periods 기간 — 시간 순, 마지막이 진행 중. 계정이 없으면 빈 목록
 * @param topics 기간 가운데 한 번이라도 관심 값이 있는 주제 중분류 — 태그 체계 순
 */
record InterestHistoryResponse(InterestHistorySpan span, List<InterestSpanChoicesResponse> spans,
		List<InterestPeriodResponse> periods, List<InterestTopicResponse> topics) {
}
