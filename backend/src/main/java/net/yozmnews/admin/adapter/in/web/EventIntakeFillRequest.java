package net.yozmnews.admin.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 사건 묶기 채우기의 기간(일) — 지금부터 그 일수 안에 처음 본 기사를 채운다. docs/specs/admin.md §3
 */
record EventIntakeFillRequest(@NotNull Integer days) {

}
