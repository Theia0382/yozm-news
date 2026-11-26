package net.yozmnews.platform.application;

import java.util.Objects;

import net.yozmnews.platform.AppClock;
import net.yozmnews.platform.domain.RoundRules;
import net.yozmnews.platform.domain.RoundRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 회차 기록 지우기 유스케이스 — 끝난 지 보존 기간({@link RoundRules#KEEP})이 지난 기록을 지운다 · docs/specs/platform.md §4 · 반복 회차.
 */
@Service
public class RoundRunCleanup {

	private static final Logger logger = LoggerFactory.getLogger(RoundRunCleanup.class);

	private final RoundRunRepository runs;

	private final AppClock clock;

	RoundRunCleanup(RoundRunRepository runs, AppClock clock) {
		this.runs = Objects.requireNonNull(runs, "runs");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 보존 기간이 지난 기록을 지운다.
	 * @return 지운 행 수
	 */
	public int run() {
		int deleted = this.runs.deleteEndedBefore(this.clock.instant().minus(RoundRules.KEEP));
		if (deleted > 0) {
			logger.info("보존 기간이 지난 회차 기록 {}건을 지웠다", deleted);
		}
		return deleted;
	}

}
