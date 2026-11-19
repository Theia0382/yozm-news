package net.yozmnews.digest.domain.selection;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.event.EventId;
import net.yozmnews.tag.TopicStatus;
import org.jspecify.annotations.Nullable;

/**
 * 사건 하나의 공유 값 — 평가 있는 소속 기사 전부로 센다. 사용자와 무관하고 저장하지 않는다 — 정본 · docs/specs/digest.md §5.
 * <p>
 * 주제 중분류 · 지역은 {@link EventShares} 의 몫 규칙, 중요도는 소속 기사 중요도의 최대, {@code low_confidence} 는 소속 기사가 모두
 * {@link TopicStatus#LOW_CONFIDENCE} 인가다. 키는 모두 활성 태그 체계 버전의 키다.
 *
 * @param id 사건
 * @param subtopics 사건의 주제 중분류 — 비어 있을 수 있다
 * @param regions 사건의 지역
 * @param importance 소속 기사 중요도의 최대 — 중요도가 있는 기사가 없으면 null
 * @param lowConfidence 평가 있는 소속 기사가 모두 {@code low_confidence} 인가
 */
public record EventFacts(EventId id, Set<String> subtopics, Set<String> regions, @Nullable Double importance,
		boolean lowConfidence) {

	public EventFacts {
		Objects.requireNonNull(id, "id");
		subtopics = Set.copyOf(Objects.requireNonNull(subtopics, "subtopics"));
		regions = Set.copyOf(Objects.requireNonNull(regions, "regions"));
	}

	/**
	 * @param members 그 사건의 평가 있는 소속 기사 — 활성 키로 푼 평가
	 * @throws IllegalArgumentException {@code members} 가 비었을 때 — 평가 있는 소속 기사가 없는 사건은 값이 없다
	 */
	public static EventFacts of(EventId id, Collection<ActiveEvaluation> members) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(members, "members");
		if (members.isEmpty()) {
			throw new IllegalArgumentException("평가 있는 소속 기사가 없는 사건이다: " + id.value());
		}
		List<ActiveEvaluation> evaluated = List.copyOf(members);
		Double importance = evaluated.stream()
			.map(ActiveEvaluation::importance)
			.filter(Objects::nonNull)
			.max(Double::compare)
			.orElse(null);
		boolean lowConfidence = evaluated.stream()
			.allMatch(member -> member.topicStatus() == TopicStatus.LOW_CONFIDENCE);
		return new EventFacts(id, EventShares.keys(evaluated.stream().map(ActiveEvaluation::subtopics).toList()),
				EventShares.keys(evaluated.stream().map(ActiveEvaluation::regions).toList()), importance, lowConfidence);
	}

}
