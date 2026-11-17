package net.yozmnews.llm.adapter.out.sdk;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.domain.provider.GenerationOutcome;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.llm.domain.retry.RetryAfter;
import org.jspecify.annotations.Nullable;

/**
 * 공급자 요청 하나가 답을 받지 못한 까닭 — 두 공급자 클라이언트가 확인 요청과 생성 요청에서 함께 쓴다 · docs/specs/llm.md llm-R8 · R9 ·
 * R20.
 *
 * @param message 고정 문구 · 상태 코드 · 예외 클래스 이름뿐이다
 * @param retryAfter 응답의 {@code retry-after} 를 읽은 값 — 없거나 읽지 못하면 null
 * @param deadlinePassed 전송이 응답 마감을 넘겼는가
 */
record ProviderFailure(LlmFailureKind kind, String message, @Nullable Duration retryAfter, boolean deadlinePassed) {

	ProviderFailure {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(message, "message");
	}

	/** 응답 없이 끝난 전송의 실패 */
	static ProviderFailure of(TransportFailure failure) {
		return new ProviderFailure(failure.failureKind(), failure.getMessage(), null,
				failure.kind() == TransportFailure.Kind.DEADLINE);
	}

	/** 응답 헤더의 {@code retry-after} 값들에서 첫 값을 읽는다 */
	static @Nullable Duration retryAfter(List<String> values) {
		return values.isEmpty() ? null : RetryAfter.parse(values.get(0)).orElse(null);
	}

	ProbeResult.Failed toProbe() {
		return new ProbeResult.Failed(this.kind, this.message);
	}

	GenerationOutcome.Failed toGeneration() {
		return new GenerationOutcome.Failed(this.kind, this.message, this.retryAfter, this.deadlinePassed);
	}

}
