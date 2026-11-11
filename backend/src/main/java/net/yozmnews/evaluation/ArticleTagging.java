package net.yozmnews.evaluation;

import java.util.Collection;
import java.util.Map;

/**
 * 태그 판정 — 기사 하나를 넘긴 질문 묶음으로 평가하고 판정 원본을 남긴다 · docs/specs/evaluation.md §3 · evaluation-R40 ~
 * evaluation-R42.
 * <p>
 * 사용자 정보를 받지 않는다 — 공유 판정이다.
 */
public interface ArticleTagging {

	/**
	 * 기사 하나를 질문 묶음으로 판정한다. 외부 호출이라 작업 역할의 작업에서, DB 트랜잭션 밖에서 부른다.
	 * @throws IllegalStateException 트랜잭션 안에서 불렀거나 평가기가 없는 프로세스일 때, 또는 판정 결과가 질문 묶음과 어긋나거나 원본
	 * 응답을 저장할 수 없을 때(원본을 남기지 않는다)
	 */
	TaggingJudgement judge(TaggingQuery query);

	/**
	 * 판정 원본의 원본 응답을 넘긴 질문 묶음으로 다시 읽는다 — 모델을 부르지 않아 부르는 쪽 트랜잭션 안에서 불러도 된다.
	 * @throws IllegalStateException 평가기가 없는 프로세스일 때 — 원본을 읽기 전에 본다
	 */
	TagReread reread(TagJudgementId judgement, TagQuestions questions);

	/**
	 * 판정 원본의 평가기 · 모델 버전 · 입력 토큰 · 판정 시각 — 없는 ID 는 빠진다. 평가기 없이 동작하고, 부르는 쪽 트랜잭션이 있으면 그
	 * 안에서 읽는다.
	 */
	Map<TagJudgementId, TagJudgementView> find(Collection<TagJudgementId> judgements);

}
