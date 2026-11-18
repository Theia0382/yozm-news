package net.yozmnews.user.adapter.in.web;

import net.yozmnews.user.application.TopicSuggestionDeclining.DeclineOutcome;
import org.jspecify.annotations.Nullable;

/**
 * 구독 제안 거절 결과 — 판정 결과 200. docs/specs/user.md §3 · user-R64
 *
 * @param result 적음 · 체계 버전이 바뀜
 * @param topic 적은 주제 중분류 키 — 적었을 때만
 * @param activeTagSetId 지금 활성 버전 ID — 체계 버전이 바뀌었을 때만
 */
record TopicSuggestionDeclineResponse(Result result, @Nullable String topic, @Nullable Long activeTagSetId) {

	static TopicSuggestionDeclineResponse of(DeclineOutcome outcome) {
		return switch (outcome) {
			case DeclineOutcome.Declined declined ->
				new TopicSuggestionDeclineResponse(Result.DECLINED, declined.subtopic(), null);
			case DeclineOutcome.TagSetChanged changed ->
				new TopicSuggestionDeclineResponse(Result.TAG_SET_CHANGED, null, changed.active().value());
		};
	}

	/** 거절 결과의 판별자 */
	enum Result {

		/** 적었다 */
		DECLINED,

		/** 화면이 본 체계 버전이 지금 활성 버전이 아니다 — 제안을 다시 읽는다 */
		TAG_SET_CHANGED

	}

}
