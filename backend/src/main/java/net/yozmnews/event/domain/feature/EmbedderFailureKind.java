package net.yozmnews.event.domain.feature;

/**
 * 임베더 실패 갈래 — 임베더가 예상한 이유로 답하지 못했을 때 그 까닭의 종류 · docs/specs/event.md §4 · event-R6.
 * <p>
 * 갈래마다 무엇을 할지(회차 멈춤 · 기사 단위 분리)는 부르는 쪽이 정한다.
 */
public enum EmbedderFailureKind {

	/** 시간이 지나면 풀릴 수 있다 — 연결 · 마감 · 요청 제한 · 서버 오류 */
	TRANSIENT,
	/** 넘긴 입력 때문이라 같은 입력을 다시 보내도 같다 */
	INPUT_REJECTED,
	/** 임베더 자체를 쓸 수 없다 — 키 무효 · 모델 없음 · 읽을 수 없는 응답 · 추론 오류 */
	EMBEDDER_UNAVAILABLE

}
