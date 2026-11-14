package net.yozmnews.credential.adapter.in.web;

import net.yozmnews.credential.application.RegistrationOutcome;
import net.yozmnews.llm.LlmFailureKind;
import org.jspecify.annotations.Nullable;

/**
 * 키 등록의 결과 — 판정 결과 200. docs/specs/credential.md §3 · credential-R10 · credential-R19
 *
 * @param result 저장됨 · 거절
 * @param failure 거절의 실패 종류 — 저장됐으면 null
 * @param key 저장한 키 — 거절이면 null
 */
record KeyRegistrationResponse(KeyRegistrationResult result, @Nullable LlmFailureKind failure,
		@Nullable LlmKeyResponse key) {

	static KeyRegistrationResponse of(RegistrationOutcome outcome) {
		return switch (outcome) {
			case RegistrationOutcome.Registered registered ->
				new KeyRegistrationResponse(KeyRegistrationResult.REGISTERED, null, LlmKeyResponse.of(registered.key()));
			case RegistrationOutcome.Rejected rejected ->
				new KeyRegistrationResponse(KeyRegistrationResult.REJECTED, rejected.kind(), null);
		};
	}

	/** 키 등록의 판별자 */
	enum KeyRegistrationResult {

		/** 검증을 통과해 저장했다 */
		REGISTERED,

		/** 검증에 실패해 저장하지 않았다 */
		REJECTED

	}

}
