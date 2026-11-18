package net.yozmnews.digest.domain.view;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/**
 * 날짜의 이웃 — 그 사용자의 다이제스트 행이 있는 날짜 가운데 바로 앞 · 뒤. docs/specs/digest.md §4 · digest-R38
 *
 * @param previous 바로 앞 날짜 — 없으면 null
 * @param next 바로 뒤 날짜 — 없으면 null
 */
public record DigestNeighbors(@Nullable LocalDate previous, @Nullable LocalDate next) {
}
