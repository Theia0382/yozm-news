package net.yozmnews.evaluation.domain.evaluator;

import net.yozmnews.evaluation.TagQuestions;

/**
 * 공유 평가 · 소속 판정(사건 소속 · 스토리라인 소속) · 관계 판정의 포트 — Jev 를 부르는 유일한 포트 · docs/specs/evaluation.md §4
 */
public interface ArticleEvaluator {

	/** 기사 하나를 넘긴 질문 묶음으로 평가한다 — docs/specs/evaluation.md §4 */
	EvaluationResult evaluate(EvaluationInput input, TagQuestions questions);

	/** 저장된 원본 응답을 넘긴 질문 묶음로 다시 읽는다 — docs/specs/evaluation.md §4 */
	RereadResult reread(String evaluator, String rawResponse, TagQuestions questions);

	/** 기사 하나를 후보마다 묻는다 — 무엇을 묻는지(사건 소속 · 스토리라인 소속)는 넘긴 입력의 문구가 정한다 · docs/specs/evaluation.md §4 */
	MembershipResult judgeMembership(MembershipInput input);

	/** 두 스토리라인의 관계를 묻는다 — 문구는 넘긴 입력에서 읽는다 · docs/specs/evaluation.md §4 */
	RelationResult judgeRelation(RelationInput input);

	/**
	 * 저장된 관계 판정 원본 응답을 그 원본을 남긴 문구 판의 답으로 다시 읽는다 — 요청을 보내지 않는다 · docs/specs/evaluation.md §4 ·
	 * evaluation-R50
	 */
	RelationRereadResult rereadRelation(String evaluator, String rawResponse, RelationWording wording);

}
