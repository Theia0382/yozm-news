package net.yozmnews.credential.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

/**
 * 키 등록 요청 — 문자열 표현이 키를 숨긴다. docs/specs/credential.md §3 · credential-R19
 *
 * @param key 등록할 키 — 비었거나 공백뿐이면 본문 형식 400
 */
record KeyRegistrationRequest(@NotBlank String key) {

	@Override
	public String toString() {
		return "KeyRegistrationRequest[key=숨김]";
	}

}
