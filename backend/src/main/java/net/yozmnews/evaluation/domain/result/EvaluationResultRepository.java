package net.yozmnews.evaluation.domain.result;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EvaluationCursor;
import net.yozmnews.evaluation.EvaluationId;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.overview.EvaluationSample;
import net.yozmnews.evaluation.domain.review.StoredKeys;
import org.jspecify.annotations.Nullable;

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
	 * 기사들의 버전 번호가 가장 큰 평가의 중요도 기대값.
	 * @return 기사 ID → 중요도 — 평가가 없거나 그 평가의 중요도가 null 이면 빠진다
	 */
	Map<ArticleId, Double> findLatestImportances(Collection<ArticleId> articleIds);

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

	/**
	 * 그 버전의 평가 가운데 평가 시각이 {@code [from, to)} 이고 커서 뒤인 것과 그 태그 행을 평가 시각 · ID 내림차순으로 한 건씩 넘긴다 —
	 * sink 가 거짓을 돌려주면 멈춘다. {@code required} 가 있으면 그 태그 종류의 그 키들 가운데 하나의 저장 행이 있는 평가만이다(거르기의
	 * 필요조건 — 붙었는지는 판정이 정한다). {@code formatKey} 가 있으면 대표 형식이 그것인 평가만이다. 커서로 나눠 받으므로 트랜잭션 안에서
	 * 부른다.
	 * @param after 커서 — 그 평가 다음부터, 없으면 처음부터
	 * @throws IllegalStateException 트랜잭션 밖에서 불렀을 때
	 */
	void scanNewestFirst(TagSetId version, Instant from, Instant to, @Nullable EvaluationCursor after,
			@Nullable StoredKeys required, @Nullable String formatKey, Predicate<ListedEvaluation> sink);

	/**
	 * 그 버전의 평가 가운데 평가 시각이 {@code [from, to)} 인 것의 ID 를 넘긴다 — 순서 없음. 커서로 나눠 받으므로 트랜잭션 안에서 부른다.
	 * @throws IllegalStateException 트랜잭션 밖에서 불렀을 때
	 */
	void streamIds(TagSetId version, Instant from, Instant to, LongConsumer sink);

	/** 평가들과 그 태그 행 — 순서 없음, 없는 ID 는 빠진다 */
	List<ListedEvaluation> findListed(Collection<EvaluationId> ids);

	/** 평가 하나와 그 분포 · 태그 행 */
	Optional<StoredEvaluationDetail> findDetail(EvaluationId id);

	/**
	 * 평가 시각이 {@code [from, to)} 이고 평가 ID 가 {@code afterId} 보다 큰 평가를 평가 ID 순으로 {@code limit} 건 읽는다 — 평가 행을
	 * 키 공유 잠금으로 잡아 그 트랜잭션 동안 지우기가 기다린다. 트랜잭션 안에서 부른다
	 */
	List<StoredResponse> lockResponses(Instant from, Instant to, long afterId, int limit);

	/** 그 평가에 없는 태그 행만 더한다 — 더한 행 수. 있는 행은 그대로다 */
	int addMissingTags(long evaluationId, ArticleId articleId, List<StoredTag> tags);

	/** 평가 시각이 {@code [from, to)} 인 평가 수를 버전별로 센다 */
	Map<TagSetId, Long> countByVersion(Instant from, Instant to);

	/**
	 * 기사들의 평가 · 태그 행을 지운다 — 지운 기사를 따른다. 평가 행을 평가 ID 순으로 먼저 잠근 뒤 태그 행 · 평가 행 순으로 지운다 — 그 평가를
	 * 잠근 채움이 있으면 그 커밋을 기다려 채움이 더한 행까지 지운다
	 */
	void deleteByArticles(Collection<ArticleId> articleIds);

}
