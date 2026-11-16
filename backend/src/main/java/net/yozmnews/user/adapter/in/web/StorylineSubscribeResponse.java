package net.yozmnews.user.adapter.in.web;

import net.yozmnews.user.application.StorylineSubscriptions.SubscribeOutcome;
import org.jspecify.annotations.Nullable;

/**
 * 스토리라인 구독 결과 — 판정 결과 200. docs/specs/user.md §3 · user-R52
 *
 * @param result 구독 · 이미 구독 · 없는 스토리라인 · 상한
 * @param storylineId 구독한(이미 구독한) 스토리라인 — 합쳐진 ID 를 받았으면 남는 스토리라인. 구독했거나 이미 구독했을 때만
 * @param limit 구독 상한 — 상한에 걸렸을 때만
 */
record StorylineSubscribeResponse(Result result, @Nullable Long storylineId, @Nullable Integer limit) {

	static StorylineSubscribeResponse of(SubscribeOutcome outcome) {
		return switch (outcome) {
			case SubscribeOutcome.Subscribed subscribed ->
				new StorylineSubscribeResponse(Result.SUBSCRIBED, subscribed.id().value(), null);
			case SubscribeOutcome.AlreadySubscribed already ->
				new StorylineSubscribeResponse(Result.ALREADY_SUBSCRIBED, already.id().value(), null);
			case SubscribeOutcome.UnknownStoryline unknown ->
				new StorylineSubscribeResponse(Result.UNKNOWN_STORYLINE, null, null);
			case SubscribeOutcome.LimitReached limited ->
				new StorylineSubscribeResponse(Result.LIMIT_REACHED, null, limited.limit());
		};
	}

	/** 구독 결과의 판별자 */
	enum Result {

		/** 구독했다 */
		SUBSCRIBED,

		/** 이미 구독했다 — 바꾸지 않았다 */
		ALREADY_SUBSCRIBED,

		/** 없는 스토리라인이다 */
		UNKNOWN_STORYLINE,

		/** 구독 수가 상한이다 — 바꾸지 않았다 */
		LIMIT_REACHED

	}

}
