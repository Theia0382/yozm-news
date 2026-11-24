package net.yozmnews.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.account.domain.guard.LimitKey;
import net.yozmnews.account.domain.guard.LimitWindow;
import net.yozmnews.account.domain.guard.RequestCounts;
import org.junit.jupiter.api.Test;

/**
 * 끝난 요청 제한 창 정리 — 지금을 기준으로 저장소에 정리를 맡기고 지운 수를 돌려준다.
 * <p>
 * 지키는 규칙: account-R34
 */
class RequestCountCleanupTests {

	@Test
	void forgetsWindowsThatEndedBeforeNow() {
		RecordingCounts counts = new RecordingCounts(4);
		RequestCountCleanup cleanup = new RequestCountCleanup(counts,
				InstantSource.fixed(Instant.parse("2026-11-14T10:20:00Z")));

		assertThat(cleanup.run()).isEqualTo(4);
		assertThat(counts.cutoffs).containsExactly(Instant.parse("2026-11-14T10:20:00Z"));
	}

	/** 정리 요청의 기준 시각만 남기는 저장소 */
	private static final class RecordingCounts implements RequestCounts {

		private final int forgotten;

		private final List<Instant> cutoffs = new ArrayList<>();

		RecordingCounts(int forgotten) {
			this.forgotten = forgotten;
		}

		@Override
		public int increment(LimitKey key, LimitWindow window) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int forgetEndedBefore(Instant cutoff) {
			this.cutoffs.add(cutoff);
			return this.forgotten;
		}

	}

}
