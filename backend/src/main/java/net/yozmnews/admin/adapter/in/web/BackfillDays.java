package net.yozmnews.admin.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 범위 밖 채울 기간의 400 — 빠진 태그 채움 · 사건 묶기 채우기 · 스토리라인 잇기 채우기가 함께 쓴다. 상한은 그 모듈의 공개 API 가 갖고,
 * 범위도 공개 API 가 판정한다(admin-R3).
 */
final class BackfillDays {

	private BackfillDays() {
	}

	/** @param max 채울 기간의 상한(일) — 공개 API 의 {@code MAX_DAYS} */
	static ResponseStatusException outOfRange(int max) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "채울 기간은 1일에서 " + max + "일까지다");
	}

}
