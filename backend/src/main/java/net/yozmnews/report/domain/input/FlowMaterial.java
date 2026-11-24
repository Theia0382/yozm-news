package net.yozmnews.report.domain.input;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineState;
import org.jspecify.annotations.Nullable;

/**
 * 흐름 재료 — 추적 중인 사용자 스토리라인 하나 · docs/specs/report.md §5 · report-R16
 *
 * @param head 헤드 — 흐름을 가리키고 인용하는 공용 스토리라인 ID
 * @param tops 추적 대상(포함 나무의 꼭대기들) — 헤드를 든다
 * @param summary 그 사용자 스토리라인의 정리 — 없으면 null
 * @param headState 헤드의 상태
 * @param trackedInPeriod 기간 안에 사용자가 추적을 더했는가
 * @param articles 기간 보도량(사건 배정의 발행 시각)과 직전 값
 * @param events 사건 줄 — 기간 안 기사 수 내림차순
 */
public record FlowMaterial(StorylineId head, Set<StorylineId> tops, @Nullable FlowSummary summary,
		StorylineState headState, boolean trackedInPeriod, Count articles, List<EventLine> events) {

	/**
	 * @throws IllegalArgumentException 추적 대상에 헤드가 없을 때
	 */
	public FlowMaterial {
		Objects.requireNonNull(head, "head");
		tops = Set.copyOf(Objects.requireNonNull(tops, "tops"));
		Objects.requireNonNull(headState, "headState");
		Objects.requireNonNull(articles, "articles");
		events = List.copyOf(Objects.requireNonNull(events, "events"));
		if (!tops.contains(head)) {
			throw new IllegalArgumentException("흐름의 추적 대상에 헤드가 없다: " + head + " · " + tops);
		}
	}

}
