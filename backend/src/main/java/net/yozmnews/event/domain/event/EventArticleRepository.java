package net.yozmnews.event.domain.event;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.assignment.DecidedBy;
import net.yozmnews.event.domain.assignment.StoredCandidate;
import org.jspecify.annotations.Nullable;

/**
 * 배정 저장소 — 배정 행과 그 기사의 배정 후보 · docs/specs/event.md §4.
 */
public interface EventArticleRepository {

	/** 기사의 배정이 있다 */
	boolean exists(ArticleId articleId);

	/**
	 * 배정이 없으면 배정과 그 후보를 쓴다 — 이미 있으면 아무것도 바꾸지 않는다.
	 * @param stored 저장 기준 이상인 후보
	 * @return 썼으면 true
	 */
	boolean insertIfAbsent(EventArticle row, List<StoredCandidate> stored);

	/**
	 * 기준이 {@code criteriaId} 가 아닌 배정을 기사 ID 순으로 {@code limit} 개까지 잠근다(다른 트랜잭션이 잡은 행은 건너뛴다). 저장 후보는
	 * 사라진 사건을 뺀다. 사건은 잠그지 않는다 — 사건의 기사 수가 필요한 적용은 사건을 잠근 뒤 읽는다. 트랜잭션 안에서 부른다.
	 */
	List<RejudgeTarget> lockRejudgeTargets(long criteriaId, int limit);

	/** 잠근 배정을 다시 적는다 — 사건 · 점수 · 정한 것 · 기준 · 배정 시각 */
	void reassign(ArticleId articleId, long eventId, @Nullable Double score, DecidedBy by, long criteriaId,
			Instant now);

	/** 잠근 배정의 기준만 새것으로 적는다 — 배정은 그대로 */
	void keep(ArticleId articleId, long criteriaId);

	/** 기사들의 배정을 기사 ID 순으로 잠근다 — 배정이 없는 기사는 결과에 없다. 트랜잭션 안에서 부른다 */
	List<EventArticle> lockByArticles(Collection<ArticleId> articleIds);

	/** 사건에 남은 배정의 발행 시각 범위 — 배정이 없으면 빈 값 */
	Optional<PublishedSpan> publishedSpan(long eventId);

	/** 기사들의 배정과 그 기사들의 배정 후보를 지운다 */
	void deleteByArticles(Collection<ArticleId> articleIds);

}
