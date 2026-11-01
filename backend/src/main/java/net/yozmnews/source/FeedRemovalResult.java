package net.yozmnews.source;

import java.util.Objects;

/**
 * 피드 숨기기 · 삭제의 결과 — docs/specs/source.md §3
 */
public sealed interface FeedRemovalResult {

	/** 숨겼다 · 지웠다 — 이미 숨긴 피드를 숨기면 아무것도 바꾸지 않고 이 결과다 */
	record Removed() implements FeedRemovalResult {
	}

	/** 숨기지 · 지우지 않았다 — 아무것도 바꾸지 않았다 */
	record Refused(FeedRemovalRefusal reason) implements FeedRemovalResult {

		public Refused {
			Objects.requireNonNull(reason, "reason");
		}

	}

}
