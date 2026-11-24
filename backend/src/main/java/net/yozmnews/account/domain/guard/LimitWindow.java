package net.yozmnews.account.domain.guard;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 요청 제한의 고정 창 — 시작은 포함, 끝은 제외. docs/specs/account.md §4 · account-R31
 *
 * @param start 창 시작
 * @param end 창 끝 — 시작보다 뒤
 */
public record LimitWindow(Instant start, Instant end) {

	public LimitWindow {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		if (!end.isAfter(start)) {
			throw new IllegalArgumentException("창 끝은 시작보다 뒤다");
		}
	}

	/** 이 시각을 담는 창 — 유닉스 기원부터 길이로 나눈 칸이다 */
	public static LimitWindow containing(Instant now, Duration length) {
		Objects.requireNonNull(now, "now");
		Objects.requireNonNull(length, "length");
		long seconds = length.toSeconds();
		if (seconds < 1 || length.getNano() != 0) {
			throw new IllegalArgumentException("창 길이는 1초 이상의 초 단위다: " + length);
		}
		Instant start = Instant.ofEpochSecond(Math.floorDiv(now.getEpochSecond(), seconds) * seconds);
		return new LimitWindow(start, start.plus(length));
	}

}
