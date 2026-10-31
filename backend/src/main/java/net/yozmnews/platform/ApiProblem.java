package net.yozmnews.platform;

import java.net.URI;
import java.util.Objects;

/**
 * 오류 응답 본문 — RFC 9457 Problem Details 의 필드 이름을 따른다. docs/specs/platform.md §3.
 *
 * @param type 문제 유형 — 따로 정하지 않은 문제는 {@code about:blank}
 * @param title 문제 유형의 요약 — {@code about:blank} 면 상태 코드의 표준 문구
 * @param status HTTP 상태 코드
 * @param detail 이 요청에 대한 설명
 * @param instance 요청 경로(질의 문자열 제외)
 */
public record ApiProblem(URI type, String title, int status, String detail, String instance) {

	public ApiProblem {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(detail, "detail");
		Objects.requireNonNull(instance, "instance");
	}

}
