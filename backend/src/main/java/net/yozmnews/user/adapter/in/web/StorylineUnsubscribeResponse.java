package net.yozmnews.user.adapter.in.web;

import net.yozmnews.user.application.StorylineSubscriptions.UnsubscribeOutcome;

/**
 * 스토리라인 구독 해제 결과 — 판정 결과 200. docs/specs/user.md §3 · user-R52
 *
 * @param result 해제 · 구독하지 않음
 */
record StorylineUnsubscribeResponse(Result result) {

	static StorylineUnsubscribeResponse of(UnsubscribeOutcome outcome) {
		return switch (outcome) {
			case UnsubscribeOutcome.Unsubscribed unsubscribed -> new StorylineUnsubscribeResponse(Result.UNSUBSCRIBED);
			case UnsubscribeOutcome.NotSubscribed not -> new StorylineUnsubscribeResponse(Result.NOT_SUBSCRIBED);
		};
	}

	/** 해제 결과의 판별자 */
	enum Result {

		/** 해제했다 */
		UNSUBSCRIBED,

		/** 구독하지 않은 스토리라인이다 — 바꾸지 않았다 */
		NOT_SUBSCRIBED

	}

}
