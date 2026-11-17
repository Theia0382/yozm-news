package net.yozmnews.feedback.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.feedback.ArticleFeedback;
import net.yozmnews.feedback.FeedbackChange;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.feedback.domain.record.FeedbackRepository;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자 조회 {@link ArticleFeedback} 의 구현 — docs/specs/feedback.md §4 · feedback-R8. 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class ArticleFeedbackService implements ArticleFeedback {

	private final FeedbackRepository feedback;

	ArticleFeedbackService(FeedbackRepository feedback) {
		this.feedback = Objects.requireNonNull(feedback, "feedback");
	}

	@Override
	@Transactional(readOnly = true)
	public Set<ArticleId> dislikedAmong(UserId user, Collection<ArticleId> articles) {
		Objects.requireNonNull(user, "user");
		long distinct = articles.stream().distinct().count();
		if (distinct > MAX_ARTICLES) {
			throw new IllegalArgumentException("한 번에 묻는 기사는 " + MAX_ARTICLES + "개까지다: " + distinct);
		}
		Set<ArticleId> disliked = new LinkedHashSet<>();
		this.feedback.currentAmong(user, articles).forEach((article, value) -> {
			if (value == FeedbackValue.DISLIKE) {
				disliked.add(article);
			}
		});
		return Collections.unmodifiableSet(disliked);
	}

	@Override
	@Transactional(readOnly = true)
	public List<FeedbackChange> changes(UserId user, Instant from, Instant to) {
		Objects.requireNonNull(user, "user");
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("기간의 시작이 끝보다 늦다: " + from + " > " + to);
		}
		return this.feedback.changes(user, from, to);
	}

}
