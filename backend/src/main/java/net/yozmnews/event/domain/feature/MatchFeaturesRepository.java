package net.yozmnews.event.domain.feature;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import net.yozmnews.article.ArticleId;

/**
 * 기사 표현 저장소 — 표현 행은 만든 뒤 바꾸지 않는다 · docs/specs/event.md §4.
 */
public interface MatchFeaturesRepository {

	/**
	 * 없으면 쓴다 — (기사, 모델 버전)이 이미 있으면 그대로다(event-R4).
	 * @return 썼으면 true
	 */
	boolean insertIfAbsent(MatchFeatures features);

	/** 기사 하나의 그 모델 버전 표현 — 없으면 빈 값 */
	Optional<MatchFeatures> find(ArticleId articleId, String modelVersion);

	/** 기사들의 그 모델 버전 표현 — 없는 기사는 결과에 없다. 기사 ID 순 */
	List<MatchFeatures> findAll(Collection<ArticleId> articleIds, String modelVersion);

	/** 기사들의 표현을 모든 모델 버전에 걸쳐 지운다 — 지운 기사 따르기(event-R8) */
	void deleteByArticles(Collection<ArticleId> articleIds);

	/**
	 * 배정의 사건이 닫힌 기사의 표현을 모든 모델 버전에 걸쳐 {@code limit} 건까지 지운다 — (기사, 모델 버전) 순으로 잠그고 다른 트랜잭션이 잡은
	 * 행은 건너뛴다.
	 * @return 지운 수
	 */
	int deleteOfClosedEvents(int limit);

	/**
	 * 만든 시각이 {@code createdBefore} 보다 이르고 그 기사가 그 모델 버전의 열린 사건에 배정되지 않은 표현을 {@code limit} 건까지 지운다 —
	 * 배정 대기에 있는 기사의 표현도 지운다(배정 회차가 다시 만든다). 잠금은 {@link #deleteOfClosedEvents} 와 같다.
	 * @return 지운 수
	 */
	int deleteUnassigned(Instant createdBefore, int limit);

}
