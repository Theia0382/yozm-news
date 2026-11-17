package net.yozmnews.digest.domain.selection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * 구독 주제별 다양성 몫과 자리 수 상한 — 정본 — docs/specs/digest.md §5
 * <p>
 * 1차로 구독 행마다 몫(= ⌈자리 ÷ 후보가 있는 구독 행 수⌉)까지 순위대로 담고, 2차로 남은 자리를 순위대로 채운다 — 다른 행에 후보가 없으면
 * 몫이 풀린다. 여러 행에 걸친 기사는 겹친 행 가운데 남은 몫이 가장 큰 행(같으면 행 순서)의 몫 하나만 쓴다. 결과는 순위 순서다.
 */
public final class DiversityQuota {

	private DiversityQuota() {
	}

	/**
	 * @param ranked 순위대로 된 구독 후보 — 사건마다 하나
	 * @param seats 구독 후보가 쓸 자리 수 — 0 이하면 빈 목록
	 */
	public static List<ArticleFacts> apply(List<ArticleFacts> ranked, UserFilter filter, int seats) {
		Objects.requireNonNull(ranked, "ranked");
		Objects.requireNonNull(filter, "filter");
		if (seats <= 0 || ranked.isEmpty()) {
			return List.of();
		}
		List<List<String>> rows = new ArrayList<>(ranked.size());
		Set<String> rowsWithCandidates = new LinkedHashSet<>();
		for (ArticleFacts article : ranked) {
			List<String> matched = filter.subscriptions(article);
			rows.add(matched);
			rowsWithCandidates.addAll(matched);
		}
		int quota = rowsWithCandidates.isEmpty() ? seats : ceilDiv(seats, rowsWithCandidates.size());
		Map<String, Integer> left = new HashMap<>();
		rowsWithCandidates.forEach(row -> left.put(row, quota));

		boolean[] taken = new boolean[ranked.size()];
		int used = 0;
		for (int i = 0; i < ranked.size() && used < seats; i++) {
			String row = roomiest(rows.get(i), left);
			if (row != null) {
				left.merge(row, -1, Integer::sum);
				taken[i] = true;
				used++;
			}
		}
		for (int i = 0; i < ranked.size() && used < seats; i++) {
			if (!taken[i]) {
				taken[i] = true;
				used++;
			}
		}
		List<ArticleFacts> kept = new ArrayList<>(used);
		for (int i = 0; i < ranked.size(); i++) {
			if (taken[i]) {
				kept.add(ranked.get(i));
			}
		}
		return List.copyOf(kept);
	}

	/** 겹친 행 가운데 남은 몫이 가장 큰 행 — 같으면 앞 행. 남은 몫이 있는 행이 없으면 null */
	private static @Nullable String roomiest(List<String> matched, Map<String, Integer> left) {
		@Nullable String best = null;
		int bestLeft = 0;
		for (String row : matched) {
			int remaining = left.getOrDefault(row, 0);
			if (remaining > bestLeft) {
				best = row;
				bestLeft = remaining;
			}
		}
		return best;
	}

	private static int ceilDiv(int dividend, int divisor) {
		return (dividend + divisor - 1) / divisor;
	}

}
