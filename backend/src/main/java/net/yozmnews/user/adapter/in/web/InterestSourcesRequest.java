package net.yozmnews.user.adapter.in.web;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 제외 소스 저장 — 고를 소스 안에서 통째로 바꾼다. 칸이 없거나 ID 가 1 미만이면 400 이다. docs/specs/user.md §3 · user-R49
 *
 * @param excluded 제외할 소스 ID
 */
record InterestSourcesRequest(@NotNull List<@NotNull @Positive Long> excluded) {
}
