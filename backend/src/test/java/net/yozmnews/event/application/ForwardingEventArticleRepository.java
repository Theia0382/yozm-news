package net.yozmnews.event.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.DecidedBy;
import net.yozmnews.event.domain.assignment.StoredCandidate;
import net.yozmnews.event.domain.event.EventArticle;
import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.PublishedSpan;
import net.yozmnews.event.domain.event.RejudgeTarget;
import org.jspecify.annotations.Nullable;

/** 시험 도우미 — 배정 저장소를 그대로 넘기는 대역의 바탕. 동시성 시험이 필요한 메서드만 바꾼다 */
class ForwardingEventArticleRepository implements EventArticleRepository {

	private final EventArticleRepository delegate;

	ForwardingEventArticleRepository(EventArticleRepository delegate) {
		this.delegate = delegate;
	}

	@Override
	public boolean exists(ArticleId articleId) {
		return this.delegate.exists(articleId);
	}

	@Override
	public boolean insertIfAbsent(EventArticle row, List<StoredCandidate> stored) {
		return this.delegate.insertIfAbsent(row, stored);
	}

	@Override
	public List<RejudgeTarget> lockRejudgeTargets(long criteriaId, int limit) {
		return this.delegate.lockRejudgeTargets(criteriaId, limit);
	}

	@Override
	public void reassign(ArticleId articleId, long eventId, @Nullable Double score, DecidedBy by, long criteriaId,
			Instant now) {
		this.delegate.reassign(articleId, eventId, score, by, criteriaId, now);
	}

	@Override
	public void keep(ArticleId articleId, long criteriaId) {
		this.delegate.keep(articleId, criteriaId);
	}

	@Override
	public List<EventArticle> lockByArticles(Collection<ArticleId> articleIds) {
		return this.delegate.lockByArticles(articleIds);
	}

	@Override
	public Optional<PublishedSpan> publishedSpan(long eventId) {
		return this.delegate.publishedSpan(eventId);
	}

	@Override
	public void deleteByArticles(Collection<ArticleId> articleIds) {
		this.delegate.deleteByArticles(articleIds);
	}

}
