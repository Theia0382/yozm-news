package net.yozmnews.source;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 피드 문서 전체를 읽지 못한 문제 — docs/specs/source.md §3.
 * <p>
 * 문구는 피드에서 이름만 인용한다(source-R46). 위치를 모르면 줄 · 열은 null 이다.
 */
public record DocumentProblemView(DocumentProblemKind kind, String message, @Nullable Integer line,
		@Nullable Integer column) {

	public DocumentProblemView {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(message, "message");
	}

}
