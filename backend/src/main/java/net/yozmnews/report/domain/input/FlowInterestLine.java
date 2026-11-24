package net.yozmnews.report.domain.input;

import java.util.Objects;

import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;

/**
 * 흐름 관심 줄 — 사용자 스토리라인 하나의 시작 · 끝 흐름 관심과 변화 · docs/specs/report.md §5 · report-R17
 *
 * @param head 헤드 — 흐름 관심을 이 꼭대기로 읽는다
 * @param summary 그 사용자 스토리라인의 정리 — 없으면 null
 * @param start 기간 시작의 흐름 관심 — 없으면 null
 * @param end 기간 끝의 흐름 관심 — 없으면 null
 */
public record FlowInterestLine(StorylineId head, @Nullable FlowSummary summary, @Nullable Double start,
		@Nullable Double end, InterestShift shift) {

	public FlowInterestLine {
		Objects.requireNonNull(head, "head");
		Objects.requireNonNull(shift, "shift");
	}

}
