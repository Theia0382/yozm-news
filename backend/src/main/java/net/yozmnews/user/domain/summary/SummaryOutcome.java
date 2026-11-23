package net.yozmnews.user.domain.summary;

import java.util.Objects;

import net.yozmnews.llm.GenerationStop;

/**
 * 정리 호출 하나의 결과 — 생성기가 돌려준다. docs/specs/user.md §4
 */
public sealed interface SummaryOutcome {

	/** 공급자가 답했다 — 원문은 파싱 · 검증 전이다 */
	record Generated(String text, int inputTokens, int outputTokens, GenerationStop stop) implements SummaryOutcome {

		public Generated {
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(stop, "stop");
			if (inputTokens < 0 || outputTokens < 0) {
				throw new IllegalArgumentException("토큰 수는 0 이상이다");
			}
		}

	}

	/** 공급자가 답하지 못했다 */
	record Failed(SummaryFailure failure) implements SummaryOutcome {

		public Failed {
			Objects.requireNonNull(failure, "failure");
		}

	}

	/** 쓸 모델 공급자의 저장한 키가 없어 보내지 않았다 */
	record KeyMissing() implements SummaryOutcome {
	}

}
