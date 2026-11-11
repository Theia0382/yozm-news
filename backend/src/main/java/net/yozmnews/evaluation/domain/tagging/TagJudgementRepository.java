package net.yozmnews.evaluation.domain.tagging;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.evaluation.TagJudgementView;

/**
 * 태그 판정 원본 저장소 — docs/specs/evaluation.md §4.
 * <p>
 * 판정 원본은 넣기 한 번이고 갱신하지 않는다 — aggregate 가 없다.
 */
public interface TagJudgementRepository {

	/** 한 행 — 넣은 행의 ID */
	TagJudgementId insert(TagJudgementRecord record);

	/** 다시 읽기 — 평가기 이름 · 원본 응답 */
	Optional<StoredTagJudgement> find(TagJudgementId id);

	/** 평가기 · 모델 버전 · 입력 토큰 · 판정 시각 — 없는 ID 는 빠진다 */
	Map<TagJudgementId, TagJudgementView> views(Collection<TagJudgementId> ids);

	/** 판정 중 기사가 지워졌을 때 방금 쓴 원본을 지운다 */
	void delete(TagJudgementId id);

	/** 지운 기사 따르기 — 판정한 기사 ID 로 지운다(evaluation-R44) */
	void deleteByArticles(Collection<ArticleId> articleIds);

}
