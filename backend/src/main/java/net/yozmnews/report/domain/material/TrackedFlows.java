package net.yozmnews.report.domain.material;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.StorylineSubscriptionChange;

/**
 * 기간 안에 추적을 더한 흐름의 정본 · docs/specs/report.md §5 · report-R16
 * <p>
 * 원인이 사용자({@code USER})이고 더함인 이력 행만 본다 — 갈라짐 · 합침 · 지움은 사용자의 행동이 아니다. 행의 지금 스토리라인을 포함 나무의
 * 꼭대기로 올려, 그 꼭대기를 가진 흐름의 헤드를 낸다.
 */
public final class TrackedFlows {

	private TrackedFlows() {
	}

	/**
	 * @param changes 기간 안의 추적 이력 행
	 * @param tops 행의 지금 스토리라인 → 포함 나무의 꼭대기
	 * @param flows 지금 흐름의 헤드 → 꼭대기들
	 * @return 기간 안에 추적을 더한 흐름의 헤드
	 */
	public static Set<StorylineId> startedIn(List<StorylineSubscriptionChange> changes,
			Map<StorylineId, StorylineId> tops, Map<StorylineId, Set<StorylineId>> flows) {
		Objects.requireNonNull(changes, "changes");
		Objects.requireNonNull(tops, "tops");
		Objects.requireNonNull(flows, "flows");
		Set<StorylineId> heads = new LinkedHashSet<>();
		for (StorylineSubscriptionChange change : changes) {
			if (change.cause() != StorylineSubscriptionCause.USER || change.action() != StorylineSubscriptionAction.ADD
					|| change.current() == null) {
				continue;
			}
			StorylineId top = tops.get(change.current());
			if (top == null) {
				continue;
			}
			flows.forEach((head, flowTops) -> {
				if (flowTops.contains(top)) {
					heads.add(head);
				}
			});
		}
		return Set.copyOf(heads);
	}

}
