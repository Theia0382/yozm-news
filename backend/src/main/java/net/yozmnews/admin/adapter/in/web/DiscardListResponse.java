package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import net.yozmnews.platform.DiscardOutcome;
import net.yozmnews.platform.DiscardResult;

/**
 * 폐기의 결과 — 발행마다 판별자 {@code result}. docs/specs/admin.md §3 · admin-R1
 */
record DiscardListResponse(List<DiscardItemResponse> results) {

	static DiscardListResponse of(List<DiscardResult> results) {
		return new DiscardListResponse(results.stream()
			.map((result) -> new DiscardItemResponse(result.id().toString(), result.outcome()))
			.toList());
	}

	/** 발행 하나의 결과 — ID 는 UUID 문자열 */
	record DiscardItemResponse(String id, DiscardOutcome result) {

	}

}
