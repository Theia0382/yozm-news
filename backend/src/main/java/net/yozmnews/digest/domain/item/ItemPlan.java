package net.yozmnews.digest.domain.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

import net.yozmnews.digest.domain.interest.InterestWeights;
import net.yozmnews.digest.domain.selection.CandidateRank;
import net.yozmnews.digest.domain.selection.SelectedEvent;

/**
 * 항목의 순위와 주요 뉴스 · 헤드라인 가르기 — 정본 · docs/specs/digest.md §5 · digest-R47
 * <p>
 * 항목 점수는 항목 안 사건 점수({@link CandidateRank#score})의 최대다 — 점수가 있는 사건이 없으면 맨 뒤. 순서는 항목 점수 내림차순 → 항목의
 * 가장 작은 사건 ID 오름차순. 큰 판정 나무의 항목({@link DigestItemPlan#bigRow()} 있음)은 모두 주요 뉴스, 나머지는 순서대로
 * {@link ItemRules#TOP_STORIES} 개까지 주요 뉴스, 그 뒤는 헤드라인이다. 항목 안 사건은 사건 순위({@link CandidateRank#order})로 둔다.
 */
public final class ItemPlan {

	private ItemPlan() {
	}

	/**
	 * @param weights 사건 점수의 관심도 가중치 — 후보 선별의 순위와 같은 값
	 * @return 주요 뉴스 항목(순서대로) 다음 헤드라인 항목(순서대로)
	 */
	public static List<ArrangedItem> arrange(List<DigestItemPlan> items, InterestWeights weights) {
		Objects.requireNonNull(items, "items");
		Objects.requireNonNull(weights, "weights");
		Comparator<SelectedEvent> eventOrder = Comparator.comparing(SelectedEvent::event,
				CandidateRank.order(weights));
		List<DigestItemPlan> ordered = items.stream()
			.map(item -> new DigestItemPlan(item.events().stream().sorted(eventOrder).toList(), item.bigRow()))
			.sorted(Comparator.comparing((DigestItemPlan item) -> score(item, weights).isEmpty())
				.thenComparing(item -> score(item, weights).orElse(0), Comparator.reverseOrder())
				.thenComparingLong(ItemPlan::smallestEvent))
			.toList();
		List<ArrangedItem> topStories = new ArrayList<>();
		List<ArrangedItem> headlines = new ArrayList<>();
		int others = 0;
		for (DigestItemPlan item : ordered) {
			if (item.bigRow() != null) {
				topStories.add(new ArrangedItem(item, ItemKind.TOP_STORY));
			}
			else if (others < ItemRules.TOP_STORIES) {
				others++;
				topStories.add(new ArrangedItem(item, ItemKind.TOP_STORY));
			}
			else {
				headlines.add(new ArrangedItem(item, ItemKind.HEADLINE));
			}
		}
		List<ArrangedItem> arranged = new ArrayList<>(topStories);
		arranged.addAll(headlines);
		return List.copyOf(arranged);
	}

	/** 항목 점수 — 항목 안 사건 점수의 최대, 점수가 있는 사건이 없으면 빈 값 */
	static OptionalDouble score(DigestItemPlan item, InterestWeights weights) {
		return item.events()
			.stream()
			.map(event -> CandidateRank.score(event.event(), weights))
			.filter(OptionalDouble::isPresent)
			.mapToDouble(OptionalDouble::getAsDouble)
			.max();
	}

	private static long smallestEvent(DigestItemPlan item) {
		return item.events().stream().mapToLong(event -> event.event().facts().id().value()).min().orElseThrow();
	}

}
