package net.yozmnews.llm.adapter.out.sdk;

import net.yozmnews.llm.LlmFailureKind;

/**
 * 전송이 응답을 넘기지 못했다 — docs/specs/llm.md llm-R8 · R13.
 * <p>
 * 문구는 고정 문구 · 예외 클래스 이름뿐이다(llm-R9). 원인 예외를 품지 않는다 — 원인의 문구가 따라 나가지 않게 한다.
 */
final class TransportFailure extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final Kind kind;

	TransportFailure(Kind kind, String message) {
		super(message);
		this.kind = kind;
	}

	Kind kind() {
		return this.kind;
	}

	/** 응답 없이 끝난 실패는 일시 장애, 상한을 넘은 본문과 닫은 뒤의 요청은 예상 밖이다 */
	LlmFailureKind failureKind() {
		return switch (this.kind) {
			case CONNECT, DEADLINE, IO, INTERRUPTED -> LlmFailureKind.TRANSIENT;
			case TOO_LARGE, CLOSED -> LlmFailureKind.UNEXPECTED;
		};
	}

	/** 넘기지 못한 까닭 */
	enum Kind {

		/** 연결 마감을 넘었다 */
		CONNECT,
		/** 요청 하나의 마감을 넘었다 */
		DEADLINE,
		/** 주고받지 못했다 — 연결 거부 · 끊김 */
		IO,
		/** 기다리다 중단됐다 */
		INTERRUPTED,
		/** 응답 본문이 상한을 넘었다 */
		TOO_LARGE,
		/** 닫은 전송에 요청했다 */
		CLOSED

	}

}
