package net.yozmnews.credential.adapter.in.web;

import net.yozmnews.credential.application.RecheckOutcome;
import net.yozmnews.llm.LlmFailureKind;
import org.jspecify.annotations.Nullable;

/**
 * 다시 검증의 결과 — 판정 결과 200. docs/specs/credential.md §3 · credential-R12 · credential-R19
 *
 * @param result 유효 · 무효 · 알 수 없음 · 키 없음
 * @param failure 무효 · 알 수 없음의 실패 종류 — 그 밖에는 null
 * @param key 확인 뒤의 지금 키 — 키가 없으면 null
 */
record KeyCheckResponse(KeyCheckResult result, @Nullable LlmFailureKind failure, @Nullable LlmKeyResponse key) {

	static KeyCheckResponse of(RecheckOutcome outcome) {
		return switch (outcome) {
			case RecheckOutcome.Missing missing -> new KeyCheckResponse(KeyCheckResult.MISSING, null, null);
			case RecheckOutcome.Valid valid ->
				new KeyCheckResponse(KeyCheckResult.VALID, null, LlmKeyResponse.of(valid.key()));
			case RecheckOutcome.Invalid invalid ->
				new KeyCheckResponse(KeyCheckResult.INVALID, invalid.kind(), LlmKeyResponse.of(invalid.key()));
			case RecheckOutcome.Undetermined undetermined -> new KeyCheckResponse(KeyCheckResult.UNDETERMINED,
					undetermined.kind(), LlmKeyResponse.of(undetermined.key()));
		};
	}

	/** 다시 검증의 판별자 */
	enum KeyCheckResult {

		/** 공급자가 성공으로 답했다 */
		VALID,

		/** 공급자가 키를 무효로 만드는 실패로 답했다 */
		INVALID,

		/** 상태를 바꾸지 않는 실패다 */
		UNDETERMINED,

		/** 저장한 키가 없다 */
		MISSING

	}

}
