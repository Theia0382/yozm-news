package net.yozmnews.evaluation;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleId;

/**
 * 관계 판정에서 견주는 흐름 하나 — 흐름 ID 와 그 흐름을 설명하는 서술 사건(부르는 쪽이 고른다) · docs/specs/evaluation.md §3 ·
 * evaluation-R48.
 *
 * @param flowId 흐름 ID — 부르는 쪽의 값이고 이 모듈은 그 뜻을 모른다
 * @param happenings 흐름의 사건 수 — 서술 사건 수가 아니라 흐름 전체의 수
 * @param firstAt 흐름의 첫 기사 시각
 * @param lastAt 흐름의 마지막 기사 시각
 * @param described 서술 사건 — 이 순서로 요청에 싣는다
 */
public record FlowDescriptor(long flowId, int happenings, Instant firstAt, Instant lastAt,
		List<FlowHappening> described) {

	/** 흐름 하나의 서술 사건 상한 — 정본. 측정 전 목표 */
	public static final int MAX_DESCRIBED = 10;

	/** 그 가운데 요약문을 싣는 서술 사건의 상한 — 정본. 측정 전 목표 */
	public static final int MAX_SUMMARIZED = 3;

	/**
	 * @throws IllegalArgumentException 흐름 ID · 사건 수가 1 미만이거나, 첫 기사 시각이 마지막 기사 시각보다 늦거나, 서술 사건이 없거나
	 * {@value #MAX_DESCRIBED}개를 넘거나, 서술 기사가 겹치거나, 요약문을 싣는 서술이 {@value #MAX_SUMMARIZED}개를 넘을 때
	 */
	public FlowDescriptor {
		if (flowId < 1) {
			throw new IllegalArgumentException("흐름 ID 는 1 이상이다: " + flowId);
		}
		if (happenings < 1) {
			throw new IllegalArgumentException("흐름의 사건 수는 1 이상이다: " + happenings);
		}
		Objects.requireNonNull(firstAt, "firstAt");
		Objects.requireNonNull(lastAt, "lastAt");
		if (firstAt.isAfter(lastAt)) {
			throw new IllegalArgumentException("첫 기사 시각이 마지막 기사 시각보다 늦다: " + firstAt + " > " + lastAt);
		}
		described = List.copyOf(described);
		if (described.isEmpty()) {
			throw new IllegalArgumentException("서술 사건이 없다");
		}
		if (described.size() > MAX_DESCRIBED) {
			throw new IllegalArgumentException("서술 사건은 " + MAX_DESCRIBED + "개까지다: " + described.size());
		}
		Set<ArticleId> articleIds = new HashSet<>();
		int summarized = 0;
		for (FlowHappening happening : described) {
			if (!articleIds.add(happening.articleId())) {
				throw new IllegalArgumentException("서술 기사가 겹친다: " + happening.articleId().value());
			}
			if (happening.summarized()) {
				summarized++;
			}
		}
		if (summarized > MAX_SUMMARIZED) {
			throw new IllegalArgumentException("요약문을 싣는 서술 사건은 " + MAX_SUMMARIZED + "개까지다: " + summarized);
		}
	}

}
