package net.yozmnews.digest;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 보이는 다이제스트의 섹션 하나 — 개요 · 주요 뉴스 · 추가 헤드라인 묶음. docs/specs/digest.md §3 · digest-R7 · digest-R38
 *
 * @param kind 섹션 종류
 * @param title 주요 뉴스의 제목 — 다른 종류면 null
 * @param body 개요 · 주요 뉴스의 본문 — 추가 헤드라인 묶음이면 null
 * @param citations 인용 — 위치 순. 개요는 비어 있다
 */
public record DigestSectionView(SectionKind kind, @Nullable String title, @Nullable String body,
		List<DigestCitation> citations) {

	public DigestSectionView {
		Objects.requireNonNull(kind, "kind");
		citations = List.copyOf(Objects.requireNonNull(citations, "citations"));
	}

}
