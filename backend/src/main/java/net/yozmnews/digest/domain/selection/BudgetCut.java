package net.yozmnews.digest.domain.selection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.event.EventId;

/**
 * 입력 토큰 예산으로 자르기 — 정본 — docs/specs/digest.md §5
 * <p>
 * 큰 슬롯 사건 다음 구독 사건의 순서로 놓고 토큰 어림의 합이 예산을 넘으면 구독 사건의 꼬리부터, 그래도 넘으면 큰 슬롯의 꼬리(마지막 행의 마지막
 * 사건)부터 뺀다. 토큰 어림이 없는 사건(표본 기사의 내용을 하나도 읽지 못한 — 그 사이 지운 기사뿐인 사건)은 뺀다.
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
		while (total > budget && !subscribed.isEmpty()) {
			total -= tokens.get(subscribed.removeLast().facts().id());
		}
		while (total > budget && !big.isEmpty()) {
			total -= tokens.get(big.removeLast().facts().id());
			bigRows.removeLast();
		}
		return new Shortlist(big, subscribed, bigRows);
	}

	private static List<CandidateEvent> known(List<CandidateEvent> events, Map<EventId, Integer> tokens) {
		return new ArrayList<>(events.stream().filter(event -> tokens.containsKey(event.facts().id())).toList());
	}

	private static long sum(List<CandidateEvent> events, Map<EventId, Integer> tokens) {
		return events.stream().mapToLong(event -> tokens.get(event.facts().id())).sum();
	}

}
