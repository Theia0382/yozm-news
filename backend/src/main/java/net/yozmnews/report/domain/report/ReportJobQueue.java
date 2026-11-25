package net.yozmnews.report.domain.report;

import java.util.UUID;

import net.yozmnews.report.ReportId;

/**
 * 리포트 생성 작업 등록 포트 — 작업 저장소(외부 경계). 같은 토큰의 작업은 한 번만 등록된다. docs/specs/report.md §4
 */
public interface ReportJobQueue {

	/** 리포트의 생성 작업을 그 토큰을 작업 ID 로 등록한다 */
	void enqueue(ReportId id, UUID token);

}
