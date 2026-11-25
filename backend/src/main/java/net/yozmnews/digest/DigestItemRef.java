package net.yozmnews.digest;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import net.yozmnews.event.EventId;
import org.jspecify.annotations.Nullable;

/**
 * 날짜별로 보이는 다이제스트의 항목 하나 — 스토리라인에 묶인 지난 항목을 찾아볼 때 쓴다. docs/specs/digest.md §3
 *
 * @param date 다이제스트 날짜
 * @param position 그 다이제스트 안의 섹션 위치
 * @param kind 주요 뉴스 항목 또는 헤드라인 항목
 * @param title 항목의 제목 — 없으면 null
 * @param events 항목의 사건 — 섹션 사건 위치 순
 */
public record DigestItemRef(LocalDate date, int position, SectionKind kind, @Nullable String title,
		List<EventId> events) {

	public DigestItemRef {
		Objects.requireNonNull(date, "date");
		Objects.requireNonNull(kind, "kind");
		events = List.copyOf(Objects.requireNonNull(events, "events"));
	}

}
