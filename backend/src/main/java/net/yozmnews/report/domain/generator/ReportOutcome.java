package net.yozmnews.report.domain.generator;

import java.util.Objects;

import net.yozmnews.llm.GenerationStop;
import net.yozmnews.report.ReportFailure;

/**
 * 생성 한 번의 결과 · docs/specs/report.md §4 · report-R2
 */
public sealed interface ReportOutcome {

	/**
	 * 공급자가 답했다 — 본문은 읽기 · 대조 전이다.
	 */
	record Generated(String text, TokenUsage usage, GenerationStop stop) implements ReportOutcome {

		public Generated {
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(usage, "usage");
			Objects.requireNonNull(stop, "stop");
		}

	}

	/**
	 * 공급자가 답하지 못했거나 보내지 않았다 — 토큰이 없다.
	 * @param message 운영 로그용 문구 — 키 · 프롬프트 · 응답 본문이 없다
	 */
	record Failed(ReportFailure failure, String message) implements ReportOutcome {

		public Failed {
			Objects.requireNonNull(failure, "failure");
			Objects.requireNonNull(message, "message");
		}

	}

}
