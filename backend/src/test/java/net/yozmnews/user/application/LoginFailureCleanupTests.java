package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.user.domain.throttle.EmailDigest;
import net.yozmnews.user.domain.throttle.FailureRecord;
import net.yozmnews.user.domain.throttle.LoginFailures;
import org.junit.jupiter.api.Test;

/**
 * 실패 기록 정리 유스케이스 — 지금에서 24시간 전을 기준으로 저장소에 정리를 맡기고 지운 수를 돌려준다.
 * <p>
 * 지키는 규칙: user-R19
 */
class LoginFailureCleanupTests {

	@Test
	void forgetsRecordsWhoseLastFailureIsMoreThanADayAgo() {
		RecordingFailures failures = new RecordingFailures(3);
		LoginFailureCleanup cleanup = new LoginFailureCleanup(failures,
				InstantSource.fixed(Instant.parse("2026-11-13T09:00:00Z")));

		int forgotten = cleanup.run();

		assertThat(forgotten).isEqualTo(3);
		assertThat(failures.cutoffs).containsExactly(Instant.parse("2026-11-12T09:00:00Z"));
	}

	/** 정리 요청의 기준 시각만 남기는 저장소 */
	private static final class RecordingFailures implements LoginFailures {

		private final int forgotten;

		private final List<Instant> cutoffs = new ArrayList<>();

		RecordingFailures(int forgotten) {
			this.forgotten = forgotten;
		}

		@Override
		public FailureRecord lock(EmailDigest digest) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void record(EmailDigest digest, FailureRecord record) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void forget(EmailDigest digest) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int forgetOlderThan(Instant cutoff) {
			this.cutoffs.add(cutoff);
			return this.forgotten;
		}

	}

}
