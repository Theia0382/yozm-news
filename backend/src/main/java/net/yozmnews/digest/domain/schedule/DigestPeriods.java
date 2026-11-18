package net.yozmnews.digest.domain.schedule;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.digest.domain.digest.DigestPeriod;
import org.jspecify.annotations.Nullable;

/**
 * 다이제스트 기간 산식의 정본 — 일정과 수동 생성이 함께 쓴다. docs/specs/digest.md §5
 * <p>
 * 시작은 직전에 끝난 다이제스트({@code READY} · {@code EMPTY})의 기간 끝이다 — 실패한 기간은 다음 다이제스트가 이어서 덮는다. 처음이면 끝에서
 * {@link #FIRST_PERIOD}, 직전 끝이 오래전이면 끝에서 {@link #MAX_PERIOD} 까지로 줄인다. 값은 측정 전 목표다.
 */
public final class DigestPeriods {

	/** 첫 다이제스트의 기간 */
	public static final Duration FIRST_PERIOD = Duration.ofHours(24);

	/** 기간 길이의 상한 */
	public static final Duration MAX_PERIOD = Duration.ofHours(72);

	private DigestPeriods() {
	}

	/**
	 * 끝이 {@code end} 인 다음 기간 — 끝이 시작보다 뒤가 아니면 빈 값(그 날짜는 만들지 않는다).
	 * @param lastCoveredEnd 직전에 끝난 다이제스트({@code READY} · {@code EMPTY})의 가장 늦은 기간 끝 — 없으면 null
	 */
	public static Optional<DigestPeriod> next(Instant end, @Nullable Instant lastCoveredEnd) {
		Objects.requireNonNull(end, "end");
		Instant start = (lastCoveredEnd == null) ? end.minus(FIRST_PERIOD)
				: latest(lastCoveredEnd, end.minus(MAX_PERIOD));
		if (!end.isAfter(start)) {
			return Optional.empty();
		}
		return Optional.of(new DigestPeriod(start, end));
	}

	private static Instant latest(Instant a, Instant b) {
		return a.isAfter(b) ? a : b;
	}

}
