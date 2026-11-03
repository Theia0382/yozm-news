package net.yozmnews.evaluation.domain.result;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.overview.EvaluationSample;

/**
 * 평가 결과 저장소 — 넣기 한 번 · 조회 · 기간 흘려 받기 · 지운 기사 따르기 · docs/specs/evaluation.md §4
 */
public interface EvaluationResultRepository {

	/** 그 기사의 그 버전 평가가 있는가 */
	boolean exists(ArticleId articleId, TagSetId tagSetId);

	/** 평가 행과 태그 행을 넣는다 — 기사 · 버전마다 하나다(고유 제약) */
	void insert(EvaluationRecord record);

	/** 기사의 평가 가운데 버전 번호가 가장 큰 것과 그 태그 행 */
	Optional<StoredEvaluation> findLatest(ArticleId articleId);

	/**
	 * 그 버전의 평가 가운데 평가 시각이 {@code [from, to)} 인 것과 그 태그 행을 평가 ID 순으로 한 건씩 넘긴다 — 결과를 담아 두지 않고 커서로
	 * 나눠 받는다. 커서는 트랜잭션 안에서만 나눠 받으므로 트랜잭션 안에서 부른다.
	 * @throws IllegalStateException 트랜잭션 밖에서 불렀을 때
	 */
	void streamVersionPeriod(TagSetId version, Instant from, Instant to, Consumer<EvaluationSample> sink);

	/**
	 * 첫 평가 시각(그 기사 평가 행의 가장 이른 평가 시각)이 {@code [from, to)} 인 기사마다 최신 버전 평가와 그 주제 태그 행을 기사 ID 순으로
	 * 한 건씩 넘긴다 — {@link #streamVersionPeriod} 와 같이 커서로 나눠 받으므로 트랜잭션 안에서 부른다.
	 * @throws IllegalStateException 트랜잭션 밖에서 불렀을 때
	 */
	void streamFirstEvaluatedIn(Instant from, Instant to, Consumer<StoredEvaluation> sink);

	/** 기사들의 평가 · 태그 행을 지운다 — 지운 기사를 따른다 */
	void deleteByArticles(Collection<ArticleId> articleIds);

}
