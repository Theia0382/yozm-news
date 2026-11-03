package net.yozmnews.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 주제별 보도량 — docs/specs/evaluation.md §3
 *
 * @param activeVersion 키 · 이름 · 순서를 정한 활성 버전
 * @param from 집계 기간의 시작(포함)
 * @param to 집계 기간의 끝(제외)
 * @param articles 첫 평가 시각이 기간 안인 기사 수
 * @param withoutActiveTopic 그 가운데 활성 주제에 하나도 이어지지 않은 기사 수 — 미분류이거나 붙은 주제가 모두 계보 없이 지워졌다
 * @param categories 고른 주제 — 체계 순서, 기사가 0 인 주제 포함
 */
public record TopicCoverageView(TagSetId activeVersion, Instant from, Instant to, long articles,
		long withoutActiveTopic, List<CategoryCoverage> categories) {

	public TopicCoverageView {
		Objects.requireNonNull(activeVersion, "activeVersion");
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		categories = List.copyOf(categories);
	}

}
