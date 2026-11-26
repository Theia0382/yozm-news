package net.yozmnews.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.TestClocks;
import net.yozmnews.platform.RoundSummary;
import net.yozmnews.platform.domain.RoundRun;
import net.yozmnews.platform.domain.RoundRunRepository;
import org.junit.jupiter.api.Test;

/**
 * 회차 기록 지우기 — 끝난 지 2일이 지난 기록을 지운다.
 * <p>
 * 지키는 규칙: platform-R133
 */
class RoundRunCleanupTests {

	@Test
	void deletesRunsThatEndedMoreThanTwoDaysAgo() {
		List<Instant> asked = new ArrayList<>();
		RoundRunRepository runs = new RoundRunRepository() {

			@Override
			public void insert(RoundRun run) {
				throw new UnsupportedOperationException();
			}

			@Override
			public RoundSummary recent(String round, Instant since) {
				throw new UnsupportedOperationException();
			}

			@Override
			public int deleteEndedBefore(Instant before) {
				asked.add(before);
				return 7;
			}

		};

		int deleted = new RoundRunCleanup(runs, TestClocks.fixed(Instant.parse("2026-11-26T12:00:00Z"))).run();

		assertThat(deleted).isEqualTo(7);
		assertThat(asked).containsExactly(Instant.parse("2026-11-24T12:00:00Z"));
	}

}
