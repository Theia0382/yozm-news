package net.yozmnews.reader.adapter.in.web;

import java.time.LocalDate;
import java.util.List;

/**
 * 관심사 히스토리의 기간 하나 — docs/specs/reader.md §5 · reader-R8
 *
 * @param firstDay 첫날(사용자 시간대)
 * @param lastDay 마지막 날(사용자 시간대)
 * @param inProgress 지금이 이 기간 안이다
 * @param entered 새로 생긴 관심 — 주제 중분류 키, 태그 체계 순
 * @param left 사라진 관심 — 주제 중분류 키, 태그 체계 순
 * @param subscriptionChanges 이 기간에 사용자가 직접 바꾼 주제 구독 — 시각 순
 */
record InterestPeriodResponse(LocalDate firstDay, LocalDate lastDay, boolean inProgress, List<String> entered,
		List<String> left, List<TopicSubscriptionStepResponse> subscriptionChanges) {
}
