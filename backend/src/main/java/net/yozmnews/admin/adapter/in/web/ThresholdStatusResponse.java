package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import net.yozmnews.tag.ThresholdChangeView;
import net.yozmnews.tag.ThresholdKindStatus;
import net.yozmnews.tag.ThresholdStatusView;

/**
 * 활성 버전의 태그 기준 · 저장 기준 상태 — docs/specs/admin.md §3 · tag-R36. 버전 ID 는 JSON 수다.
 *
 * @param changes 이력 — 최근 것부터
 */
record ThresholdStatusResponse(long version, int versionNumber, ThresholdKindStatus topic, ThresholdKindStatus region,
		List<ThresholdChangeView> changes) {

	static ThresholdStatusResponse of(ThresholdStatusView status) {
		return new ThresholdStatusResponse(status.version().value(), status.versionNumber(), status.topic(),
				status.region(), status.changes());
	}

}
