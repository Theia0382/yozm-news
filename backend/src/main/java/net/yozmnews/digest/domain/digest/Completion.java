package net.yozmnews.digest.domain.digest;

import java.time.Duration;
import java.util.Objects;

import net.yozmnews.digest.DigestFailure;
import net.yozmnews.digest.domain.generator.TokenUsage;
import org.jspecify.annotations.Nullable;

/**
 * 다이제스트를 끝내는 방법 · docs/specs/digest.md §5 · digest-R1
 */
public sealed interface Completion {

	/**
	 * 인용이 있는 다이제스트를 저장했다.
	 * @param firstMissing 처음 읽은 출력에 없던 항목 수
	 * @param substituted 끝내 대체 헤드라인으로 남긴 항목 수 — 처음 읽은 출력에 없던 수를 넘지 않는다
	 */
	record Ready(TokenUsage usage, Duration took, int firstMissing, int substituted) implements Completion {

		/**
		 * @throws IllegalArgumentException 소요 시간이 음수이거나, 항목 수가 음수이거나, 대체한 수가 처음 없던 수를 넘을 때
		 */
		public Ready {
			Objects.requireNonNull(usage, "usage");
			Objects.requireNonNull(took, "took");
			if (took.isNegative()) {
				throw new IllegalArgumentException("소요 시간이 음수다: " + took);
			}
			if (firstMissing < 0 || substituted < 0) {
				throw new IllegalArgumentException("항목 수가 음수다: " + firstMissing + ", " + substituted);
			}
			if (substituted > firstMissing) {
				throw new IllegalArgumentException("대체한 항목 수가 처음 없던 항목 수를 넘는다: " + substituted + " > " + firstMissing);
			}
		}

	}

	/** 기간에 후보가 없었다 */
	record Empty() implements Completion {
	}

	/**
	 * 생성이 실패했다.
	 * @param usage 공급자가 센 토큰 — 생성기를 불러 공급자가 답했으면 있고, 생성기를 부르지 않았거나 관문 실패 · 키 없음이면 {@code null}
	 * @param took 생성기를 불렀으면 그 소요 시간, 아니면 {@code null}
	 */
	record Failed(DigestFailure failure, String message, @Nullable TokenUsage usage, @Nullable Duration took)
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
