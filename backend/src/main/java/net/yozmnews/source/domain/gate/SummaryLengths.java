package net.yozmnews.source.domain.gate;

import java.util.List;
import java.util.Objects;

import net.yozmnews.source.FetchedArticle;
import org.jspecify.annotations.Nullable;

/**
 * 요약문 길이 분포 — 코드 포인트 수.
 *
 * @param present 요약문이 있는 기사 수
 * @param absent 요약문이 없는 기사 수
 * @param min 가장 짧은 길이 — 요약문이 있는 기사가 없으면 null
 * @param median 길이를 오름차순으로 놓은 목록에서 0 부터 세어 {@code (present - 1) / 2} 번째(아래 중앙값) — 요약문이 있는 기사가 없으면 null
 * @param max 가장 긴 길이 — 요약문이 있는 기사가 없으면 null
 */
public record SummaryLengths(int present, int absent, @Nullable Integer min, @Nullable Integer median,
		@Nullable Integer max) {

	/** 기사들의 요약문 길이 분포를 센다 */
	public static SummaryLengths of(List<FetchedArticle> articles) {
		List<Integer> lengths = articles.stream()
			.map(FetchedArticle::summary)
			.filter(Objects::nonNull)
			.map(summary -> summary.codePointCount(0, summary.length()))
			.sorted()
			.toList();
		int absent = articles.size() - lengths.size();
		if (lengths.isEmpty()) {
			return new SummaryLengths(0, absent, null, null, null);
		}
		return new SummaryLengths(lengths.size(), absent, lengths.getFirst(), lengths.get((lengths.size() - 1) / 2),
				lengths.getLast());
	}

}
