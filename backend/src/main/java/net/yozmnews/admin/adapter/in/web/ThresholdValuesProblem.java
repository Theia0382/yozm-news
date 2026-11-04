package net.yozmnews.admin.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 범위 밖 태그 기준 · 저장 기준의 400 — 범위는 공개 API 가 판정한다(admin-R3).
 */
final class ThresholdValuesProblem {

	private ThresholdValuesProblem() {
	}

	static ResponseStatusException outOfRange() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "태그 기준 · 저장 기준은 0 이상 1 이하, 소수 셋째 자리까지다");
	}

}
