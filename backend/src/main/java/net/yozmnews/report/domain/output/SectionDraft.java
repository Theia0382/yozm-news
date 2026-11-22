package net.yozmnews.report.domain.output;

import java.util.List;
import java.util.Objects;

/**
 * 읽은 출력의 갈래 칸 하나 — 제목 · 본문 · 인용 별칭(출력 순서, 대조 전) · docs/specs/report.md §5 · report-R5
 */
public record SectionDraft(String title, String body, List<String> refs) {

	public SectionDraft {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(body, "body");
		refs = List.copyOf(refs);
	}

}
