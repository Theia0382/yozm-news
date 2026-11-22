package net.yozmnews.digest;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 다이제스트 항목 한 쪽. docs/specs/digest.md §3
 *
 * @param items 항목 — 날짜 내림차순, 같은 날짜면 섹션 위치 내림차순
 * @param next 다음 쪽 커서 — 더 없으면 null
 */
public record DigestItemPage(List<DigestItemRef> items, @Nullable DigestItemCursor next) {

	/** 빈 쪽 */
	public static final DigestItemPage EMPTY = new DigestItemPage(List.of(), null);

	public DigestItemPage {
		items = List.copyOf(Objects.requireNonNull(items, "items"));
	}

}
