package net.yozmnews.digest.domain.selection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.digest.domain.interest.InterestWeights;
import net.yozmnews.event.EventId;
import net.yozmnews.storyline.StorylineId;

/**
 * 주제 구독 밖 관심 흐름의 길 — 정본 — docs/specs/digest.md §5
 * <p>
 * 관심 흐름(추적 중인 사용자 스토리라인의 꼭대기와 스토리라인 관심이 기준 이상인 꼭대기에서 거른 꼭대기를 뺀 것 —
 * {@link net.yozmnews.digest.domain.interest.StorylineLevels#followed})의 사건 가운데 구독 행과 겹치지 않아 구독 필터로는 들지 못하는
 * 사건을 고른다. 구독 행과 겹치는 관심 흐름의 사건은 이 길을 타지 않고 구독 필터의 순위로 다툰다. 사건 제외({@link UserFilter#passesEvent})를
 * 지나고 큰 슬롯이 쓰지 않은 사건을 순위({@link CandidateRank#order})로 훑어, 아직 대표가 없는 관심 흐름을 가진 사건을 담고 그 사건의 관심
 * 흐름을 모두 채운 것으로 친다 — 흐름마다 한 사건이다. 개수 상한은 두지 않는다 — 앞에서부터 고르므로 앞 n 개가 상한 n 으로 고른 결과와 같고,
 * 상한은 자리 수를 아는 {@link CandidateSelector#shortlist} 가 자른다.
 */
public final class FollowedStorylines {

	private FollowedStorylines() {
	}

	/**
	 * @param pool 후보 사건 — 구독 필터 앞({@link CandidateEvents#subscribed})
	 * @param weights 순위의 관심도 가중치
	 * @param followed 관심 흐름의 꼭대기
	 * @param taken 큰 슬롯이 쓴 사건
	 * @return 새 길 사건 — 순위 순서
	 */
	public static List<CandidateEvent> pick(Collection<CandidateEvent> pool, UserFilter filter, InterestWeights weights,
			Set<StorylineId> followed, Set<EventId> taken) {
		Objects.requireNonNull(pool, "pool");
		Objects.requireNonNull(filter, "filter");
		Objects.requireNonNull(weights, "weights");
		Objects.requireNonNull(followed, "followed");
		Objects.requireNonNull(taken, "taken");
		if (followed.isEmpty()) {
			return List.of();
		}
		List<CandidateEvent> ranked = pool.stream()
			.filter(event -> filter.passesEvent(event.facts(), event.tops()))
			.filter(event -> filter.subscriptions(event.facts()).isEmpty())
			.filter(event -> !taken.contains(event.facts().id()))
			.filter(event -> event.tops().stream().anyMatch(followed::contains))
			.sorted(CandidateRank.order(weights))
			.toList();
		Set<StorylineId> served = new HashSet<>();
		List<CandidateEvent> picked = new ArrayList<>();
		for (CandidateEvent event : ranked) {
			List<StorylineId> flows = event.tops().stream().filter(followed::contains).toList();
			if (!served.containsAll(flows)) {
				picked.add(event);
				served.addAll(flows);
			}
		}
		return List.copyOf(picked);
	}

}
