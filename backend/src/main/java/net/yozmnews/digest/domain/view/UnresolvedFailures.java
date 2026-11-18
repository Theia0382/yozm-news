package net.yozmnews.digest.domain.view;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 해결되지 않은 마지막 실패의 규칙 — 정본. docs/specs/digest.md §5 · digest-R40
 * <p>
 * 그 사용자의 가장 최근 실패(ID 가 가장 큰 {@code FAILED} 행) 뒤에, 곧 ID 가 더 큰 행 가운데 기간을 덮는 행({@code READY} · {@code EMPTY} —
 * digest-R30)이 없으면 그 실패는 해결되지 않았다. 날짜는 보지 않는다. 생성을 기다리거나 생성 중인 행은 끝나지 않았으므로 해결로 치지 않는다.
 */
public final class UnresolvedFailures {

	private UnresolvedFailures() {
	}

	/**
	 * @param latestFailed 그 사용자의 가장 최근 실패 — 없으면 빈 값
	 * @param latestCoveringId 그 사용자의 기간을 덮는 행 가운데 가장 큰 ID — 없으면 빈 값
	 * @return 해결되지 않았으면 그 실패, 실패가 없거나 해결됐으면 빈 값
	 */
	public static Optional<FailedAttempt> of(Optional<FailedAttempt> latestFailed, OptionalLong latestCoveringId) {
		Objects.requireNonNull(latestFailed, "latestFailed");
		Objects.requireNonNull(latestCoveringId, "latestCoveringId");
		return latestFailed
			.filter(failed -> latestCoveringId.isEmpty() || latestCoveringId.getAsLong() < failed.id());
	}

}
