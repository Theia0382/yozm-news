package net.yozmnews.digest;

import java.util.List;
import java.util.Objects;

import net.yozmnews.event.EventId;
import org.jspecify.annotations.Nullable;

/**
 * 보이는 다이제스트의 섹션 하나 — 개요 또는 항목 하나(주요 뉴스 · 헤드라인). docs/specs/digest.md §3 · digest-R7 · digest-R38
 *
 * @param kind 섹션 종류
 * @param title 항목의 제목 — 개요면 null
 * @param body 개요 · 주요 뉴스의 요약 · 헤드라인의 코멘트 — 코멘트가 없는 헤드라인이면 null
 * @param reason 항목의 선정 이유(화면의 배지 하나 — {@code domain/view} 의 {@code SectionReasons}) — 개요면 null
 * @param citations 인용(출처 기사) — 위치 순. 개요는 비어 있다
 * @param events 항목의 사건 — 위치 순(첫 사건이 항목 키 — 반응을 누르는 손잡이). 개요는 비어 있다
 */
public record DigestSectionView(SectionKind kind, @Nullable String title, @Nullable String body,
		@Nullable SelectionReason reason, List<DigestCitation> citations, List<EventId> events) {

	public DigestSectionView {
		Objects.requireNonNull(kind, "kind");
		citations = List.copyOf(Objects.requireNonNull(citations, "citations"));
		events = List.copyOf(Objects.requireNonNull(events, "events"));
	}

}
