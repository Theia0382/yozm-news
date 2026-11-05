package net.yozmnews.evaluation.domain.evaluator;

import net.yozmnews.evaluation.TagSetView;

/**
 * 공유 평가 · 사건 소속 판정의 포트 — Jev 를 부르는 유일한 포트 · docs/specs/evaluation.md §4
 */
public interface ArticleEvaluator {

	/** 기사 하나를 넘긴 체계의 질문으로 평가한다 — docs/specs/evaluation.md §4 */
	EvaluationResult evaluate(EvaluationInput input, TagSetView tagSet);

	/** 저장된 원본 응답을 넘긴 체계로 다시 읽는다 — docs/specs/evaluation.md §4 */
	RereadResult reread(String evaluator, String rawResponse, TagSetView tagSet);

	/** 기사 하나가 후보 사건마다 그 사건을 다루는지 묻는다 — 질문 문구는 넘긴 입력에서 읽는다 · docs/specs/evaluation.md §4 */
	MembershipResult judgeMembership(MembershipInput input);

}
