package net.yozmnews.reader.adapter.in.web;

import org.jspecify.annotations.Nullable;

/**
 * 다이제스트 홈 — 가장 늦은 날짜의 날짜 보기와 수동 생성 상태. docs/specs/reader.md §5 · reader-R9 · reader-R11
 *
 * @param day 가장 늦은 날짜의 날짜 보기 — 다이제스트가 하나도 없으면 null
 * @param status 수동 생성 상태 — 계정 · 시간대가 없으면 null
 */
record DigestHomeResponse(@Nullable DigestDayResponse day, @Nullable DigestRequestStatusResponse status) {
}
