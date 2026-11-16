package net.yozmnews.digest.domain.digest;

import java.time.Instant;
import java.util.Objects;

/**
 * 다이제스트 기간 — 끝은 시작보다 뒤
 */
public record DigestPeriod(Instant start, Instant end) {

	public DigestPeriod {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		if (!end.isAfter(start)) {
			throw new IllegalArgumentException("기간의 끝이 시작보다 뒤가 아니다: " + start + " ~ " + end);
		}
	}

}
