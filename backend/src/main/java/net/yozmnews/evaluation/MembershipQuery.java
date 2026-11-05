package net.yozmnews.evaluation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleId;

/**
 * 사건 소속 판정 요청 — 판정할 기사와 후보 사건들 · docs/specs/evaluation.md §3 · evaluation-R37.
 *
 * @param articleId 판정할 기사
 * @param candidates 후보 사건 — 이 순서로 묻는다
 */
public record MembershipQuery(ArticleId articleId, List<EventDescriptor> candidates) {

	/** 한 요청의 후보 사건 상한 — 정본. 측정 전 목표 */
	public static final int MAX_CANDIDATES = 10;

	/**
	 * @throws IllegalArgumentException 후보가 없거나 {@value #MAX_CANDIDATES}개를 넘거나, 사건 ID 가 겹치거나, 판정할 기사가 어느
	 * 후보의 서술 기사에 들어 있을 때
	 */
	public MembershipQuery {
		Objects.requireNonNull(articleId, "articleId");
		candidates = List.copyOf(candidates);
		if (candidates.isEmpty()) {
			throw new IllegalArgumentException("후보 사건이 없다");
		}
		if (candidates.size() > MAX_CANDIDATES) {
			throw new IllegalArgumentException("후보 사건은 " + MAX_CANDIDATES + "개까지다: " + candidates.size());
		}
		Set<Long> eventIds = new HashSet<>();
		for (EventDescriptor candidate : candidates) {
			if (!eventIds.add(candidate.eventId())) {
				throw new IllegalArgumentException("후보 사건 ID 가 겹친다: " + candidate.eventId());
			}
			if (candidate.articleIds().contains(articleId)) {
				throw new IllegalArgumentException("판정할 기사가 후보 사건 " + candidate.eventId() + " 의 서술 기사다");
			}
		}
	}

}
