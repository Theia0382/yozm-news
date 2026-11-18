package net.yozmnews.digest.domain.digest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.platform.UserId;

/**
 * 다이제스트 행 조회 — 일정 · 수동 생성 · 거두기가 날짜 · 기간을 정하려고 읽는다. 구현은 {@code adapter/out/persistence} 의
 * {@code JdbcDigestLedger}. docs/specs/digest.md §4
 */
public interface DigestLedger {

	/** 사용자마다 가장 늦은 다이제스트 날짜(계기 · 상태 무관) — 다이제스트가 없는 사용자는 빠진다 */
	Map<UserId, LocalDate> latestDates(Collection<UserId> users);

	/** 그 사용자의 가장 늦은 다이제스트 날짜(계기 · 상태 무관) */
	Optional<LocalDate> latestDate(UserId user);

	/** 그 사용자의 끝난 다이제스트 가운데 {@code READY} · {@code EMPTY} 인 것의 가장 늦은 기간 끝 */
	Optional<Instant> lastCoveredEnd(UserId user);

	/** 그 사용자의 그 날짜의 가장 최근(ID 가 가장 큰) 다이제스트 기간 — 계기 · 상태 무관 */
	Optional<DigestPeriod> latestPeriodOn(UserId user, LocalDate date);

	/** 그 사용자의 다이제스트 가운데 생성을 기다리거나 생성 중인 것이 있는가 */
	boolean inProgress(UserId user);

	/** 그 사용자의 수동 다이제스트 가운데 만든 시각이 {@code from} 이상 · {@code to} 미만인 것의 수 */
	int manualCountBetween(UserId user, Instant from, Instant to);

	/** 생성을 기다리고 마지막 등록 시각이 {@code before} 보다 이른 다이제스트 — 등록 시각 · ID 순으로 {@code limit} 개까지 */
	List<DigestId> stalledScheduled(Instant before, int limit);

	/** 생성 중이고 시작 시각이 {@code before} 보다 이른 다이제스트 — 시작 시각 · ID 순으로 {@code limit} 개까지 */
	List<DigestId> stalledRunning(Instant before, int limit);

}
