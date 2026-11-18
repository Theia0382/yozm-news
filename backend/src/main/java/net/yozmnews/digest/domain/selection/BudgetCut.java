package net.yozmnews.digest.domain.selection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 입력 토큰 예산으로 자르기 — 정본 — docs/specs/digest.md §5
 * <p>
 * 큰 슬롯 기사 다음 구독 후보의 순서로 놓고 토큰 어림의 합이 예산을 넘으면 구독 후보의 꼬리부터, 그래도 넘으면 큰 슬롯의 꼬리(마지막 행의 마지막
 * 사건)부터 뺀다. 토큰 어림이 없는 기사(내용을 읽지 못한 — 그 사이 지운 기사)는 뺀다.
 */
public final class BudgetCut {

	private BudgetCut() {
	}

	/**
	 * @param tokens 기사 → 토큰 어림({@link CandidateCost#candidate})
	 * @param budget 후보가 쓸 수 있는 토큰({@link CandidateCost#candidateBudget})
	 */
	public static Shortlist apply(Shortlist shortlist, Map<ArticleId, Integer> tokens, int budget) {
		Objects.requireNonNull(shortlist, "shortlist");
		Objects.requireNonNull(tokens, "tokens");
		List<ArticleFacts> big = new ArrayList<>();
		List<Integer> bigRows = new ArrayList<>();
		for (int i = 0; i < shortlist.big().size(); i++) {
			if (tokens.containsKey(shortlist.big().get(i).id())) {
				big.add(shortlist.big().get(i));
				bigRows.add(shortlist.bigRows().get(i));
			}
		}
		List<ArticleFacts> subscribed = known(shortlist.subscribed(), tokens);
		long total = sum(big, tokens) + sum(subscribed, tokens);
		while (total > budget && !subscribed.isEmpty()) {
			total -= tokens.get(subscribed.removeLast().id());
		}
		while (total > budget && !big.isEmpty()) {
			total -= tokens.get(big.removeLast().id());
			bigRows.removeLast();
		}
		return new Shortlist(big, subscribed, bigRows);
	}

	private static List<ArticleFacts> known(List<ArticleFacts> articles, Map<ArticleId, Integer> tokens) {
		return new ArrayList<>(articles.stream().filter(article -> tokens.containsKey(article.id())).toList());
	}

	private static long sum(List<ArticleFacts> articles, Map<ArticleId, Integer> tokens) {
		return articles.stream().mapToLong(article -> tokens.get(article.id())).sum();
	}

}
