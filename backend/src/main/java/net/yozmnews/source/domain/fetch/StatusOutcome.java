package net.yozmnews.source.domain.fetch;

import java.util.Objects;

import net.yozmnews.source.FetchFailureReason;

/**
 * 응답 상태 코드를 어떻게 다룰지 — {@link StatusRule#classify} 가 정한다
 */
public sealed interface StatusOutcome {

	/** 200 — 본문을 읽는다 */
	record Body() implements StatusOutcome {
	}

	/** 조건부 헤더를 보낸 요청의 304 — 바뀌지 않았다 */
	record NotModified() implements StatusOutcome {
	}

	/**
	 * 리다이렉트 — {@code Location} 으로 다음 홉을 보낸다.
	 *
	 * @param permanent 영구 이동(301 · 308)인가
	 */
	record Redirect(boolean permanent) implements StatusOutcome {
	}

	/** 실패했다 */
	record Failure(FetchFailureReason reason) implements StatusOutcome {

		public Failure {
			Objects.requireNonNull(reason, "reason");
		}

	}

}
