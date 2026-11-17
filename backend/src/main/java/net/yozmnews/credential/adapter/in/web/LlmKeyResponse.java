package net.yozmnews.credential.adapter.in.web;

import java.time.Instant;

import net.yozmnews.credential.application.KeyView;
import net.yozmnews.credential.domain.key.KeyStatus;

/**
 * 키 하나 — 힌트 · 상태 · 시각뿐이다. docs/specs/credential.md §3 · credential-R19
 *
 * @param hint 키의 끝 4자 — 짧은 키는 빈 문자열
 * @param status 상태
 * @param validatedAt 이 키로 공급자가 마지막으로 정상으로 답한 시각 — 등록 · 다시 검증 · 생성 호출이 성공할 때 고쳐진다(credential-R14)
 * @param createdAt 만든 시각
 */
record LlmKeyResponse(String hint, KeyStatus status, Instant validatedAt, Instant createdAt) {

	static LlmKeyResponse of(KeyView key) {
		return new LlmKeyResponse(key.hint(), key.status(), key.validatedAt(), key.createdAt());
	}

}
