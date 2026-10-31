package net.yozmnews.source;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 운영자 원문의 문제 하나 — 경로 · 종류 이름 · 운영자용 문구 — docs/specs/source.md §3.
 * <p>
 * 경로는 최상위면 빈 문자열이다. 위치를 모르면 줄 · 열은 null 이다.
 */
public record ProfileProblemView(String path, String code, String message, @Nullable Integer line,
		@Nullable Integer column) {

	public ProfileProblemView {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(message, "message");
	}

}
