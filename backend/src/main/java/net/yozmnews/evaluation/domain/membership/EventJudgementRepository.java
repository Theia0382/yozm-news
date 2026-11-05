package net.yozmnews.evaluation.domain.membership;

import java.util.Collection;

import net.yozmnews.article.ArticleId;

/**
 * 사건 소속 판정 원본 저장소 — 넣기 · 지운 기사 따르기 · docs/specs/evaluation.md §4
 */
public interface EventJudgementRepository {

	/** 판정 원본 한 행을 넣는다 — 같은 기사의 판정이 여러 번이면 행도 여러 개다 */
	void insert(MembershipRecord record);

	/** 기사들의 판정 원본을 지운다 — 지운 기사를 따른다. 빈 입력이면 아무것도 하지 않는다 */
	void deleteByArticles(Collection<ArticleId> articleIds);

}
