package net.yozmnews.digest.domain.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

import net.yozmnews.digest.domain.interest.InterestWeights;
import net.yozmnews.digest.domain.selection.CandidateRank;
import net.yozmnews.digest.domain.selection.SelectedEvent;
import net.yozmnews.event.EventId;

/**
 * 항목의 순위와 주요 뉴스 · 헤드라인 가르기 — 정본 · docs/specs/digest.md §5 · digest-R47
 * <p>
 * 항목 점수는 항목 안 사건 점수({@link CandidateRank#score})의 최대다 — 점수가 있는 사건이 없으면 맨 뒤. 순서는 항목 점수 내림차순 → 항목의
 * 가장 작은 사건 ID 오름차순. 항목 안 사건은 사건 순위({@link CandidateRank#order})로 둔다.
 * <p>
 * 항목의 요약문 길이는 항목 사건들의 요약문 길이({@link EventSummaryLengths} — RSS 요약문의 코드 포인트) 합이다. 요약문 길이가 기준
 * ({@link ItemRules#minSummaryLength}) 이상인 항목만 주요 뉴스가 된다 — 큰 판정 나무의 항목({@link DigestItemPlan#bigRow()} 있음)은
 * 기준 이상이면 모두 주요 뉴스, 나머지는 기준 이상인 항목이 순서대로 {@link ItemRules#TOP_STORIES} 개까지 주요 뉴스다. 그 밖은 헤드라인이다.
 * 기준보다 짧아 헤드라인이 된 항목 가운데 큰 판정 나무의 항목과, 큰 화제 밖 자리가 남아 있는 동안 만난 항목을 센다(요약문이 짧은 항목 수).
 */
public final class ItemPlan {

	private ItemPlan() {
	}

	/**
	 * @param weights 사건 점수의 관심도 가중치 — 후보 선별의 순위와 같은 값
	 * @param summaryLengths 사건마다 요약문 길이 — 항목의 모든 사건을 담는다
	 * @param minSummaryLength 주요 뉴스가 되는 항목의 요약문 길이의 최소
	 * @return 주요 뉴스 항목(순서대로) 다음 헤드라인 항목(순서대로)과 요약문이 짧은 항목 수
	 * @throws IllegalArgumentException 요약문 길이가 없는 사건이 있거나 최소가 음수일 때
	 */
	public static ItemArrangement arrange(List<DigestItemPlan> items, InterestWeights weights,
			Map<EventId, Integer> summaryLengths, int minSummaryLength) {
		Objects.requireNonNull(items, "items");
		Objects.requireNonNull(weights, "weights");
		Objects.requireNonNull(summaryLengths, "summaryLengths");
		if (minSummaryLength < 0) {
			throw new IllegalArgumentException("요약문 길이의 최소가 음수다: " + minSummaryLength);
		}
		Comparator<SelectedEvent> eventOrder = Comparator.comparing(SelectedEvent::event,
				CandidateRank.order(weights));
		List<DigestItemPlan> ordered = items.stream()
			.map(item -> new DigestItemPlan(item.events().stream().sorted(eventOrder).toList(), item.bigRow(),
					item.top()))
			.sorted(Comparator.comparing((DigestItemPlan item) -> score(item, weights).isEmpty())
				.thenComparing(item -> score(item, weights).orElse(0), Comparator.reverseOrder())
				.thenComparingLong(ItemPlan::smallestEvent))
			.toList();
		List<ArrangedItem> topStories = new ArrayList<>();
		List<ArrangedItem> headlines = new ArrayList<>();
		int others = 0;
		int shortSummary = 0;
		for (DigestItemPlan item : ordered) {
			boolean enough = summaryLength(item, summaryLengths) >= minSummaryLength;
			if (item.bigRow() != null) {
				if (enough) {
					topStories.add(new ArrangedItem(item, ItemKind.TOP_STORY));
				}
				else {
					headlines.add(new ArrangedItem(item, ItemKind.HEADLINE));
					shortSummary++;
				}
			}
			else if (others < ItemRules.TOP_STORIES) {
				if (enough) {
					others++;
					topStories.add(new ArrangedItem(item, ItemKind.TOP_STORY));
				}
				else {
					headlines.add(new ArrangedItem(item, ItemKind.HEADLINE));
					shortSummary++;
				}
			}
			else {
				headlines.add(new ArrangedItem(item, ItemKind.HEADLINE));
			}
		}
		List<ArrangedItem> arranged = new ArrayList<>(topStories);
		arranged.addAll(headlines);
		return new ItemArrangement(arranged, shortSummary);
	}

	/**
	 * 항목의 요약문 길이 — 항목 사건들의 요약문 길이 합
	 * @throws IllegalArgumentException 요약문 길이가 없는 사건이 있을 때
	 */
	private static int summaryLength(DigestItemPlan item, Map<EventId, Integer> summaryLengths) {
		int length = 0;
		for (SelectedEvent event : item.events()) {
			Integer eventLength = summaryLengths.get(event.event().facts().id());
			if (eventLength == null) {
				throw new IllegalArgumentException("요약문 길이가 없는 사건이다: " + event.event().facts().id().value());
			}
			length += eventLength;
		}
		return length;
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
