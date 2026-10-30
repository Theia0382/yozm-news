package net.yozmnews.source.domain.feed;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 피드 문서 전체를 읽지 못한 문제 — source-R46.
 *
 * @param line 줄(1부터, 사전 보정을 마친 문서 기준) — 알 수 없으면 없다
 * @param column 열(1부터) — 알 수 없으면 없다
 */
public record DocumentProblem(DocumentProblemKind kind, String message, @Nullable Integer line,
		@Nullable Integer column) {

	public DocumentProblem {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(message, "message");
	}

	static DocumentProblem of(DocumentProblemKind kind, String message) {
		return new DocumentProblem(kind, message, null, null);
	}

}
