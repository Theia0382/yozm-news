package net.yozmnews.digest.domain.candidate;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 후보 행의 보존 기한 — 정본 · docs/specs/digest.md §5 · digest-R11
 */
public final class CandidateRetention {

	/** 다이제스트를 만든 뒤 후보 행을 남기는 기간 */
	public static final Duration KEEP = Duration.ofDays(30);

	private CandidateRetention() {
	}

	/** 이 시각보다 먼저 만든 다이제스트의 후보 행을 지운다 — {@code now} − {@link #KEEP} */
	public static Instant cutoff(Instant now) {
		Objects.requireNonNull(now, "now");
		return now.minus(KEEP);
	}

}
