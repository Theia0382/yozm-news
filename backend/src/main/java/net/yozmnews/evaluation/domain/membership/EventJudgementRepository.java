package net.yozmnews.evaluation.domain.membership;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.evaluation.EventJudgementView;
import org.jspecify.annotations.Nullable;

/**
 * 사건 소속 판정 원본 저장소 — 넣기 · 기간 읽기 · 지운 기사 따르기 · docs/specs/evaluation.md §4
 */
public interface EventJudgementRepository {

	/**
	 * 판정 원본 한 행을 넣는다 — 같은 기사의 판정이 여러 번이면 행도 여러 개다.
	 * @return 넣은 행의 ID
	 */
	EventJudgementId insert(MembershipRecord record);

	/**
	 * 판정 시각이 {@code [from, to)} 인 원본 — (판정 시각, ID) 내림차순으로 {@code limit} 건. 커서가 있으면 그보다 작은 것만.
	 * @param afterJudgedAt 커서의 판정 시각 — {@code afterId} 와 함께 null 이거나 함께 있다
	 */
	List<EventJudgementView> page(Instant from, Instant to, @Nullable Instant afterJudgedAt, @Nullable Long afterId,
			int limit);

	/** 기사들의 판정 원본을 지운다 — 지운 기사를 따른다. 빈 입력이면 아무것도 하지 않는다 */
	void deleteByArticles(Collection<ArticleId> articleIds);

}
