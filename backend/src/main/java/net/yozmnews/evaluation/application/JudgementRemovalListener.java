package net.yozmnews.evaluation.application;

import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.evaluation.domain.membership.EventJudgementRepository;
import net.yozmnews.evaluation.domain.tagging.TagJudgementRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link ArticlesRemoved} 리스너 — docs/specs/evaluation.md §4 · evaluation-R44.
 * <p>
 * 지운 기사의 판정 원본을 한 트랜잭션에서 사건 소속, 태그 순으로 지운다. 비동기 · 새 트랜잭션이고 다시 처리해도 결과가 같다.
 */
@Component
class JudgementRemovalListener {

	private final EventJudgementRepository membershipJudgements;

	private final TagJudgementRepository tagJudgements;

	JudgementRemovalListener(EventJudgementRepository membershipJudgements, TagJudgementRepository tagJudgements) {
		this.membershipJudgements = membershipJudgements;
		this.tagJudgements = tagJudgements;
	}

	@ApplicationModuleListener
	public void on(ArticlesRemoved event) {
		this.membershipJudgements.deleteByArticles(event.articleIds());
		this.tagJudgements.deleteByArticles(event.articleIds());
	}

}
