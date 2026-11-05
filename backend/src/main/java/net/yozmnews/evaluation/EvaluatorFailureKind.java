package net.yozmnews.evaluation;

/**
 * 평가기 실패 갈래 — 평가기가 예상한 이유로 답하지 못했을 때 그 까닭의 종류 · docs/specs/evaluation.md §3.
 * <p>
 * 갈래마다 무엇을 할지(재시도 · 멈춤)는 부르는 쪽이 정한다.
 */
public enum EvaluatorFailureKind {

	/** 시간이 지나면 풀릴 수 있다 — 마감 초과 · 연결 실패 · 서버 오류 · 요청 제한 */
	TRANSIENT,
	/** 그 기사 때문이라 다시 보내도 같다 — 입력 거부(기사가 평가기의 입력 한도를 넘는 경우 포함) */
	ARTICLE_REJECTED,
	/** 평가기 자체를 쓸 수 없다 — 키 무효 · 한도 소진 · 설정 오류 · 응답을 읽을 수 없음 */
	EVALUATOR_UNAVAILABLE

}
