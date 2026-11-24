package net.yozmnews.event;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventJudgementId;

/**
 * 운영자 화면의 사건 조회 — 사건 배정 기준 · 합쳐진 사건 · 판정 원본의 쓰임 · 기사 하나의 배정과 표현의 핵심 말 · docs/specs/event.md §3 ·
 * event-R13 · event-R17 · event-R22.
 * <p>
 * {@code admin} 이 부른다. 사용자 정보를 받지 않는다. 트랜잭션을 열지 않는다 — 각 질의는 그 순간의 값이다.
 */
public interface EventReview {

	/** 이 프로세스의 사건 배정 기준 — 설정이다(event-R17) */
	AssignmentCriteria assignmentCriteria();

	/**
	 * 이 사건으로 합쳐진 사건 — 합친 시각 · 사건 ID 순.
	 * @return 남는 사건이 아니거나 없는 사건이면 빈 목록
	 */
	List<MergedEvent> mergedInto(EventId survivor);

	/**
	 * 판정 원본을 반영한 기록 — 배정이면 그 기사의 지금 배정, 병합이면 남긴 쌍과 지금 합쳐졌는지.
	 * @return 원본 ID → 쓰임. 가리키는 기록이 없는 원본(쓰기를 되돌렸거나, 반영한 기록이 지워졌거나, 쌍의 기록이 이미 있었거나, 기록하기 전의 원본)은 빠진다. 빈 입력이면
	 *         빈 맵
	 * @throws IllegalStateException 한 원본을 배정과 병합 판정 기록이 함께 가리킬 때 — 불변식 위반이다
	 */
	Map<EventJudgementId, JudgementUse> judgementUses(Collection<EventJudgementId> judgements);

	/**
	 * 기사 하나의 배정 — 배정 행 · 판정에 쓴 기준 · 저장 후보를 한 질의로 읽는다.
	 * @return 배정이 없는 기사면 빈 값
	 */
	Optional<ArticleAssignment> assignmentOf(ArticleId articleId);

	/**
	 * 기사 하나의 표현마다 핵심 말 — 만든 시각 · 모델 버전 순.
	 * @return 표현이 없는(아직 만들지 않았거나 정리 작업이 지운) 기사면 빈 목록
	 */
	List<ArticleKeyTerms> keyTermsOf(ArticleId articleId);

}
