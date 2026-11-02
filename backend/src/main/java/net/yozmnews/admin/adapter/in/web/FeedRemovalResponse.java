package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.source.FeedRemovalRefusal;
import net.yozmnews.source.FeedRemovalResult;
import org.jspecify.annotations.Nullable;

/**
 * 피드 숨기기 · 삭제의 결과 — {@code REFUSED} 면 {@code refusal} 이 있다. docs/specs/admin.md §3
 */
record FeedRemovalResponse(Outcome result, @Nullable FeedRemovalRefusal refusal) {

	static FeedRemovalResponse of(FeedRemovalResult result) {
		return switch (result) {
			case FeedRemovalResult.Removed removed -> new FeedRemovalResponse(Outcome.REMOVED, null);
			case FeedRemovalResult.Refused refused -> new FeedRemovalResponse(Outcome.REFUSED, refused.reason());
		};
	}

	enum Outcome {

		/** 숨겼다 · 지웠다 */
		REMOVED,
		/** 숨기지 · 지우지 않았다 */
		REFUSED

	}

}
