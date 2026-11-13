package net.yozmnews.evaluation.domain.evaluator;

import net.yozmnews.evaluation.TagQuestions;

/**
 * 공유 평가 · 사건 소속 판정 · 관계 판정의 포트 — Jev 를 부르는 유일한 포트 · docs/specs/evaluation.md §4
 */
public interface ArticleEvaluator {

	/** 기사 하나를 넘긴 질문 묶음으로 평가한다 — docs/specs/evaluation.md §4 */
	EvaluationResult evaluate(EvaluationInput input, TagQuestions questions);

	/** 저장된 원본 응답을 넘긴 질문 묶음로 다시 읽는다 — docs/specs/evaluation.md §4 */
	RereadResult reread(String evaluator, String rawResponse, TagQuestions questions);

	/** 기사 하나가 후보 사건마다 그 사건을 다루는지 묻는다 — 질문 문구는 넘긴 입력에서 읽는다 · docs/specs/evaluation.md §4 */
	MembershipResult judgeMembership(MembershipInput input);

	/** 두 흐름의 관계를 묻는다 — 문구는 넘긴 입력에서 읽는다 · docs/specs/evaluation.md §4 */
	RelationResult judgeRelation(RelationInput input);

}
