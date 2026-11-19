package net.yozmnews.digest.domain.content;

import java.util.Objects;

import net.yozmnews.digest.SelectionReason;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 섹션이 다룬 사건 한 줄 — 사건과 그 선정 이유 · docs/specs/digest.md §6
 *
 * @param eventId 사건 — 모듈 사이라 FK 가 없다
 * @param selectionReason 사건의 선정 이유
 */
@Table("digest_section_events")
public record SectionEvent(long eventId, SelectionReason selectionReason) {

	public SectionEvent {
		if (eventId < 1) {
			throw new IllegalArgumentException("사건 식별자는 1 이상이어야 한다: " + eventId);
		}
		Objects.requireNonNull(selectionReason, "selectionReason");
	}

}
