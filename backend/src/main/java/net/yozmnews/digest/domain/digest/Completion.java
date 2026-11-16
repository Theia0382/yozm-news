package net.yozmnews.digest.domain.digest;

import java.time.Duration;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import net.yozmnews.digest.domain.generator.TokenUsage;

/**
 * 다이제스트를 끝내는 방법 · docs/specs/digest.md §5 · digest-R1
 */
public sealed interface Completion {

	/** 인용이 있는 다이제스트를 저장했다 */
	record Ready(TokenUsage usage, Duration took) implements Completion {

		public Ready {
			Objects.requireNonNull(usage, "usage");
			Objects.requireNonNull(took, "took");
			if (took.isNegative()) {
				throw new IllegalArgumentException("소요 시간이 음수다: " + took);
			}
		}

	}

	/** 기간에 후보가 없었다 */
	record Empty() implements Completion {
	}

	/**
	 * 생성이 실패했다.
	 * @param usage 생성기를 불렀으면 그 사용량, 아니면 {@code null}
	 * @param took 생성기를 불렀으면 그 소요 시간, 아니면 {@code null}
	 */
	record Failed(DigestFailure failure, String message, @Nullable TokenUsage usage, @Nullable Duration took)
			implements Completion {

		public Failed {
			Objects.requireNonNull(failure, "failure");
			Objects.requireNonNull(message, "message");
			if ((usage == null) != (took == null)) {
				throw new IllegalArgumentException("사용량과 소요 시간은 함께 있거나 함께 없다");
			}
			if (took != null && took.isNegative()) {
				throw new IllegalArgumentException("소요 시간이 음수다: " + took);
			}
		}

	}

}
