package net.yozmnews.platform.domain;

import java.time.Instant;

import net.yozmnews.platform.RoundSummary;

/** 회차 기록 {@code round_runs} 를 쓰고 읽고 지운다 — docs/specs/platform.md §4 */
public interface RoundRunRepository {

	/** 끝난 회차 하나를 남긴다 */
	void insert(RoundRun run);

	/**
	 * 회차 하나의 최근 기록.
	 * @param since 창의 시작 — 이 시각 이후(포함)에 끝난 회차를 합한다
	 * @return 창 안의 합 · 회차 수와 마지막으로 끝난 회차(창 밖이어도). 기록이 없으면 {@link RoundSummary#NONE}
	 */
	RoundSummary recent(String round, Instant since);

	/**
	 * 끝난 시각이 {@code before} 보다 앞선 기록을 지운다.
	 * @return 지운 행 수
	 */
	int deleteEndedBefore(Instant before);

}
