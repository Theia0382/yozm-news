package net.yozmnews.admin.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 빠진 태그 채움의 기간(일) — 지금부터 그 일수 안에 평가된 평가를 채운다. docs/specs/admin.md §3
 */
record TagBackfillRequest(@NotNull Integer days) {

}
