package net.yozmnews.digest.domain.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

import net.yozmnews.digest.domain.interest.InterestWeights;
import net.yozmnews.digest.domain.selection.CandidateRank;
import net.yozmnews.digest.domain.selection.DiversityQuota;
import net.yozmnews.digest.domain.selection.SelectedEvent;
import net.yozmnews.digest.domain.selection.UserFilter;
import net.yozmnews.user.TopicSubscription;

/**
 * 항목의 순위와 주요 뉴스 · 헤드라인 가르기 — 정본 · docs/specs/digest.md §5 · digest-R47 · digest-R54
 * <p>
 * 항목 점수는 항목 안 사건 점수({@link CandidateRank#score})의 최대다 — 점수가 있는 사건이 없으면 맨 뒤. 순서는 항목 점수 내림차순 → 항목의
 * 가장 작은 사건 ID 오름차순. 큰 판정 나무의 항목({@link DigestItemPlan#bigRow()} 있음)은 모두 주요 뉴스, 나머지(일반 항목)에서
 * {@link ItemRules#TOP_STORIES} 개를 주요 뉴스로 고르고, 그 밖은 헤드라인이다. 항목 안 사건은 사건 순위({@link CandidateRank#order})로 둔다.
 * <p>
 * 항목의 구독 행은 항목 안 사건이 겹치는 구독 행({@link UserFilter#subscriptions})이다. 모든 항목에 나온 구독 행으로 상한을 걸면
 * ({@link ItemRules#topicCapApplies}) 행마다 몫을 {@link ItemRules#topicCap}(큰 판정 나무의 항목 수)에서 시작해, 큰 판정 나무의 항목이 순서대로
 * 몫을 쓸 행({@link DiversityQuota#roomiest})의 몫 하나를 먼저 쓰고 — 남은 몫이 없어도 주요 뉴스다 — 1차로 일반 항목을 순서대로 훑어 구독 행이
 * 없는 항목은 담고, 구독 행이 있는 항목은 몫을 쓸 행이 있으면 그 몫 하나를 쓰고 담는다. 2차로(걸지 않으면 이것만) 남은 자리를 순서대로
 * 채운다.
 */
public final class ItemPlan {

	private ItemPlan() {
	}

	/**
	 * @param weights 사건 점수의 관심도 가중치 — 후보 선별의 순위와 같은 값
	 * @param filter 사용자의 구독 행 — 후보 선별의 구독 필터와 같은 값
	 * @return 주요 뉴스 항목(순서대로) 다음 헤드라인 항목(순서대로)
	 */
	public static List<ArrangedItem> arrange(List<DigestItemPlan> items, InterestWeights weights, UserFilter filter) {
		Objects.requireNonNull(items, "items");
		Objects.requireNonNull(weights, "weights");
		Objects.requireNonNull(filter, "filter");
		Comparator<SelectedEvent> eventOrder = Comparator.comparing(SelectedEvent::event,
				CandidateRank.order(weights));
		List<DigestItemPlan> ordered = items.stream()
			.map(item -> new DigestItemPlan(item.events().stream().sorted(eventOrder).toList(), item.bigRow(),
					item.top()))
			.sorted(Comparator.comparing((DigestItemPlan item) -> score(item, weights).isEmpty())
				.thenComparing(item -> score(item, weights).orElse(0), Comparator.reverseOrder())
				.thenComparingLong(ItemPlan::smallestEvent))
			.toList();
		boolean[] picked = topStories(ordered.stream().filter(item -> item.bigRow() != null).toList(),
				ordered.stream().filter(item -> item.bigRow() == null).toList(), filter);
		List<ArrangedItem> topStories = new ArrayList<>();
		List<ArrangedItem> headlines = new ArrayList<>();
		int other = 0;
		for (DigestItemPlan item : ordered) {
			if (item.bigRow() != null) {
				topStories.add(new ArrangedItem(item, ItemKind.TOP_STORY));
			}
			else if (picked[other++]) {
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

	/**
	 * 일반 항목 가운데 주요 뉴스로 고를 것 — 큰 판정 나무의 항목이 몫을 먼저 쓰고, 구독 행마다 상한을 건 1차, 남은 자리를 순서대로 채우는 2차
	 * @param big 큰 판정 나무의 항목 — 모두 주요 뉴스, 항목 순위대로
	 * @param others 일반 항목 — 항목 순위대로
	 * @return {@code others} 의 위치마다 주요 뉴스인가
	 */
	private static boolean[] topStories(List<DigestItemPlan> big, List<DigestItemPlan> others, UserFilter filter) {
		boolean[] picked = new boolean[others.size()];
		List<List<String>> bigRows = big.stream().map(item -> rowsOf(item, filter)).toList();
		List<List<String>> rows = others.stream().map(item -> rowsOf(item, filter)).toList();
		Set<String> present = new LinkedHashSet<>();
		bigRows.forEach(present::addAll);
		rows.forEach(present::addAll);
		int used = 0;
		if (ItemRules.topicCapApplies(present.size())) {
			int cap = ItemRules.topicCap(big.size());
			Map<String, Integer> left = new HashMap<>();
			present.forEach(row -> left.put(row, cap));
			for (List<String> ofBig : bigRows) {
				String row = DiversityQuota.roomiest(ofBig, left);
				if (row != null) {
					left.merge(row, -1, Integer::sum);
				}
			}
			for (int i = 0; i < others.size() && used < ItemRules.TOP_STORIES; i++) {
				List<String> ofItem = rows.get(i);
				if (ofItem.isEmpty()) {
					picked[i] = true;
					used++;
					continue;
				}
				String row = DiversityQuota.roomiest(ofItem, left);
				if (row != null) {
					left.merge(row, -1, Integer::sum);
					picked[i] = true;
					used++;
				}
			}
		}
		for (int i = 0; i < others.size() && used < ItemRules.TOP_STORIES; i++) {
			if (!picked[i]) {
				picked[i] = true;
				used++;
			}
		}
		return picked;
	}

	/** 항목의 구독 행 — 항목 안 사건이 겹치는 구독 행, 구독 행 순서 */
	private static List<String> rowsOf(DigestItemPlan item, UserFilter filter) {
		Set<String> matched = new HashSet<>();
		item.events().forEach(event -> matched.addAll(filter.subscriptions(event.event().facts())));
		return filter.topics().stream().map(TopicSubscription::key).filter(matched::contains).toList();
	}

	private static long smallestEvent(DigestItemPlan item) {
		return item.events().stream().mapToLong(event -> event.event().facts().id().value()).min().orElseThrow();
	}

}
