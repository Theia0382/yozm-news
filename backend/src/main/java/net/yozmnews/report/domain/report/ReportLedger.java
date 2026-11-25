package net.yozmnews.report.domain.report;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportStatus;

/**
 * 리포트 행 조회 — 일정 · 생성 작업이 기간을 정하고 월간 입력을 고르려고, 거두기가 멈춘 리포트를 찾으려고 읽는다. 구현은 {@code adapter/out/persistence} 의
 * {@code JdbcReportLedger}. docs/specs/report.md §4
 */
public interface ReportLedger {

	/** 사용자마다 그 종류의 리포트 가운데 가장 늦은 기간 끝(상태 무관) — 리포트가 없는 사용자는 빠진다 */
	Map<UserId, Instant> latestEnds(Collection<UserId> users, ReportPeriodKind kind);

	/** 그 사용자의 그 종류의 리포트 가운데 가장 늦은 기간 끝(상태 무관) */
	Optional<Instant> latestEnd(UserId user, ReportPeriodKind kind);

	/**
	 * 그 사용자의 주간 리포트 가운데 기간이 [{@code start}, {@code end}) 와 겹치는 것 — 기간 시작 · ID 순. 월간이 기다리고 세고 싣는 주간(기간
	 * 안에 다 든 것)은 {@code ReportScheduleRules} 가 이 가운데서 고른다
	 */
	List<Weekly> weekliesOverlapping(UserId user, Instant start, Instant end);

	/** 생성을 기다리고 마지막 등록 시각이 {@code before} 보다 이른 리포트 — 등록 시각 · ID 순으로 {@code limit} 개까지 */
	List<ReportId> stalledScheduled(Instant before, int limit);

	/** 생성 중이고 시작 시각이 {@code before} 보다 이른 리포트 — 시작 시각 · ID 순으로 {@code limit} 개까지 */
	List<ReportId> stalledRunning(Instant before, int limit);

	/**
	 * 주간 리포트 하나.
	 * @param id 리포트
	 * @param period 기간
	 * @param status 상태
	 */
	record Weekly(ReportId id, ReportPeriod period, ReportStatus status) {
	}

}
