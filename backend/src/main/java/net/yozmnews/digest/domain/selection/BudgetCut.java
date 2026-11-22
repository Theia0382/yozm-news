package net.yozmnews.digest.domain.selection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.digest.domain.selection.BigSlotPicker.BigSlots;
import net.yozmnews.event.EventId;

/**
 * 입력 토큰 예산으로 자르기 — 정본 — docs/specs/digest.md §5
 * <p>
 * 토큰 어림의 합이 예산을 넘는 동안 하나씩 뺀다 — 하나를 뺀 뒤 큰 슬롯 사건이 남는 사건의 절반({@link SelectionRules#bigSeats})보다 많으면
 * 큰 슬롯에서, 아니면 구독 사건의 꼬리에서 뺀다. 그래서 구독 사건이 하나라도 빠졌으면 큰 슬롯 사건은 남는 사건의 절반을 넘지 않고, 구독 사건이
 * 모자라면 남는 예산은 큰 슬롯이 쓴다. 큰 슬롯에서는 행마다 고르게 뺀다({@link BigSlotPicker#trim} — 가장 많이 남은 행의 마지막 사건, 같으면
 * 뒤 행). 토큰 어림이 없는 사건(표본 기사의 내용을 하나도 읽지 못한 — 그 사이 지운 기사뿐인 사건)은 먼저 뺀다.
 */
public final class BudgetCut {

	private BudgetCut() {
	}

	/**
	 * @param tokens 사건 → 토큰 어림({@link CandidateCost#event})
	 * @param budget 후보가 쓸 수 있는 토큰({@link CandidateCost#candidateBudget})
	 */
	public static Shortlist apply(Shortlist shortlist, Map<EventId, Integer> tokens, int budget) {
		Objects.requireNonNull(shortlist, "shortlist");
		Objects.requireNonNull(tokens, "tokens");
		List<CandidateEvent> big = new ArrayList<>();
		List<Integer> bigRows = new ArrayList<>();
		for (int i = 0; i < shortlist.big().size(); i++) {
			if (tokens.containsKey(shortlist.big().get(i).facts().id())) {
				big.add(shortlist.big().get(i));
				bigRows.add(shortlist.bigRows().get(i));
			}
		}
		List<CandidateEvent> subscribed = known(shortlist.subscribed(), tokens);
		long total = sum(big, tokens) + sum(subscribed, tokens);
		while (total > budget && !(big.isEmpty() && subscribed.isEmpty())) {
			int left = big.size() + subscribed.size() - 1;
			if (big.size() > SelectionRules.bigSeats(left)) {
				BigSlots kept = evenly(big, bigRows, big.size() - 1);
				for (CandidateEvent event : big) {
					if (!kept.events().contains(event)) {
						total -= tokens.get(event.facts().id());
					}
				}
				big = new ArrayList<>(kept.events());
				bigRows = new ArrayList<>(kept.rows());
			}
			else {
				total -= tokens.get(subscribed.removeLast().facts().id());
			}
		}
		return new Shortlist(big, subscribed, bigRows);
	}

	/** 행 순서의 큰 슬롯 사건을 행마다 모아 {@code count} 개로 고르게 줄인다 */
	private static BigSlots evenly(List<CandidateEvent> big, List<Integer> bigRows, int count) {
		List<List<CandidateEvent>> chosen = new ArrayList<>();
		List<Integer> chosenRows = new ArrayList<>();
		for (int i = 0; i < big.size(); i++) {
			if (chosenRows.isEmpty() || !chosenRows.getLast().equals(bigRows.get(i))) {
				chosen.add(new ArrayList<>());
				chosenRows.add(bigRows.get(i));
			}
			chosen.getLast().add(big.get(i));
		}
		return BigSlotPicker.trim(chosen, chosenRows, count);
	}

	private static List<CandidateEvent> known(List<CandidateEvent> events, Map<EventId, Integer> tokens) {
		return new ArrayList<>(events.stream().filter(event -> tokens.containsKey(event.facts().id())).toList());
	}

	private static long sum(List<CandidateEvent> events, Map<EventId, Integer> tokens) {
		return events.stream().mapToLong(event -> tokens.get(event.facts().id())).sum();
	}

}
