package net.yozmnews.report.domain.report;

import java.time.Duration;
import java.util.Objects;

import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.domain.generator.TokenUsage;
import org.jspecify.annotations.Nullable;

/**
 * 리포트를 끝내는 방법 · docs/specs/report.md §5 · report-R1
 */
public sealed interface Completion {

	/**
	 * 섹션 · 인용을 저장했다.
	 */
	record Ready(TokenUsage usage, Duration took) implements Completion {

		/**
		 * @throws IllegalArgumentException 소요 시간이 음수일 때
		 */
		public Ready {
			Objects.requireNonNull(usage, "usage");
			Objects.requireNonNull(took, "took");
			if (took.isNegative()) {
				throw new IllegalArgumentException("소요 시간이 음수다: " + took);
			}
		}

	}

	/** 입력 갈래가 하나도 없었다 */
	record Empty() implements Completion {
	}

	/**
	 * 생성이 실패했다.
	 * @param usage 공급자가 센 토큰 — 공급자가 답했으면 있고, 생성기를 부르지 않았거나 공급자가 답하지 못했으면 {@code null}
	 * @param took 생성기를 불렀으면 그 소요 시간, 아니면 {@code null}
	 */
	record Failed(ReportFailure failure, String message, @Nullable TokenUsage usage, @Nullable Duration took)
			implements Completion {

		/**
		 * @throws IllegalArgumentException 사용량이 있는데 소요 시간이 없거나 소요 시간이 음수일 때
		 */
		public Failed {
			Objects.requireNonNull(failure, "failure");
			Objects.requireNonNull(message, "message");
			if (usage != null && took == null) {
				throw new IllegalArgumentException("사용량이 있으면 소요 시간도 있다");
			}
			if (took != null && took.isNegative()) {
				throw new IllegalArgumentException("소요 시간이 음수다: " + took);
			}
		}

	}

}
