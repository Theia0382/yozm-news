package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import net.yozmnews.platform.ResubmissionOutcome;
import net.yozmnews.platform.ResubmissionResult;

/**
 * 다시 처리의 결과 — 발행마다 판별자 {@code result}. docs/specs/admin.md §3 · admin-R1
 */
record ResubmissionListResponse(List<ResubmissionItemResponse> results) {

	static ResubmissionListResponse of(List<ResubmissionResult> results) {
		return new ResubmissionListResponse(results.stream()
			.map((result) -> new ResubmissionItemResponse(result.id().toString(), result.outcome()))
			.toList());
	}

	/** 발행 하나의 결과 — ID 는 UUID 문자열 */
	record ResubmissionItemResponse(String id, ResubmissionOutcome result) {

	}

}
