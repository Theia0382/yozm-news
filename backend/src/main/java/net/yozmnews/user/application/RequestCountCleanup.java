package net.yozmnews.user.application;

import java.time.InstantSource;
import java.util.Objects;

import net.yozmnews.user.domain.guard.RequestCounts;
import org.springframework.stereotype.Service;

/**
 * 끝난 요청 제한 창 정리 유스케이스 — 창 끝이 지금보다 이른 기록을 지운다. docs/specs/user.md §4 · user-R42
 */
@Service
public class RequestCountCleanup {

	private final RequestCounts counts;

	private final InstantSource clock;

	RequestCountCleanup(RequestCounts counts, InstantSource clock) {
		this.counts = Objects.requireNonNull(counts, "counts");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 끝난 창을 지운다.
	 * @return 지운 행 수
	 */
	public int run() {
		return this.counts.forgetEndedBefore(this.clock.instant());
	}

}
