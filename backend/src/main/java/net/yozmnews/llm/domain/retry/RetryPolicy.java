package net.yozmnews.llm.domain.retry;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.llm.LlmFailureKind;
import org.jspecify.annotations.Nullable;

/**
 * 생성 호출을 다시 보낼지와 그 전에 기다릴 시간의 정본 — docs/specs/llm.md llm-R20.
 * <p>
 * 시간이 지나면 풀릴 수 있는 실패({@link LlmFailureKind#TRANSIENT})만 다시 보낸다. 응답 마감을 넘긴 실패는 같은 마감으로 다시 보내도 같을
 * 가능성이 크고, 그 요청은 공급자 쪽에서 과금됐을 수 있어 다시 보내지 않는다. 공급자가 기다릴 시간({@code retry-after})을 주면 그것을 따르고,
 * 너무 길면 다시 보내지 않는다. 간격 · 횟수 · 상한은 측정 전의 잠정값이다 — 공급자의 일시 장애가 풀리는 시간을 재서 고친다.
 */
public final class RetryPolicy {

	/** 한 생성 호출에서 보내는 최대 횟수 — 처음 하나와 다시 보내기 셋 */
	public static final int MAX_SENDS = 4;

	/** 공급자가 기다릴 시간을 주지 않을 때의 간격 — 다시 보내는 차례마다 */
	public static final List<Duration> BACKOFF = List.of(Duration.ofSeconds(2), Duration.ofSeconds(4),
			Duration.ofSeconds(8));

	/** 따르는 {@code retry-after} 의 최댓값 — 넘으면 다시 보내지 않는다 */
	public static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(60);

	private RetryPolicy() {
	}

	/**
	 * 생성 호출 한 번이 걸릴 수 있는 가장 긴 시간 — 요청 마감마다 보내기를 {@link #MAX_SENDS} 번 하고, 그 사이마다 가장 길게 기다린 경우
	 * ({@link #MAX_RETRY_AFTER} 와 {@link #BACKOFF} 의 가장 긴 값). 산식의 정본이다.
	 * @param deadline 요청 하나의 마감
	 */
	public static Duration longestCall(Duration deadline) {
		Objects.requireNonNull(deadline, "deadline");
		Duration longestWait = BACKOFF.stream().reduce(MAX_RETRY_AFTER, (a, b) -> (a.compareTo(b) >= 0) ? a : b);
		return deadline.multipliedBy(MAX_SENDS).plus(longestWait.multipliedBy(MAX_SENDS - 1));
	}

	/**
	 * @param sentCount 지금까지 보낸 횟수 — 1 이상
	 * @param kind 마지막 실패의 종류
	 * @param retryAfter 공급자가 준 기다릴 시간 — 없으면 null
	 * @param deadlinePassed 마지막 실패가 응답 마감을 넘긴 것인가
	 * @return 다시 보내기 전에 기다릴 시간 — 비어 있으면 다시 보내지 않는다
	 */
	public static Optional<Duration> next(int sentCount, LlmFailureKind kind, @Nullable Duration retryAfter,
			boolean deadlinePassed) {
		Objects.requireNonNull(kind, "kind");
		if (sentCount < 1) {
			throw new IllegalArgumentException("보낸 횟수는 1 이상이다 — " + sentCount);
		}
		boolean passing = switch (kind) {
			case TRANSIENT -> true;
			case INVALID_KEY, PERMISSION_DENIED, QUOTA_EXHAUSTED, UNEXPECTED -> false;
		};
		if (!passing || deadlinePassed || sentCount >= MAX_SENDS) {
			return Optional.empty();
		}
		if (retryAfter != null) {
			return (retryAfter.isNegative() || retryAfter.compareTo(MAX_RETRY_AFTER) > 0) ? Optional.empty()
					: Optional.of(retryAfter);
		}
		return Optional.of(BACKOFF.get(sentCount - 1));
	}

}
