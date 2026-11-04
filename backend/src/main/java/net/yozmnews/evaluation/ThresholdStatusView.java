package net.yozmnews.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * 활성 버전의 기준 상태 — 태그 종류마다 지금 값 · 지난 저장 기준 최댓값 · 어긋난 상태와 이력 · docs/specs/evaluation.md §3 ·
 * evaluation-R34.
 *
 * @param versionNumber 활성 버전의 번호
 * @param changes 활성 버전의 태그 기준 · 저장 기준 이력 — 최근 것부터
 */
public record ThresholdStatusView(TagSetId version, int versionNumber, ThresholdKindStatus topic,
		ThresholdKindStatus region, List<ThresholdChangeView> changes) {

	public ThresholdStatusView {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(topic, "topic");
		Objects.requireNonNull(region, "region");
		changes = List.copyOf(changes);
	}

}
