package net.yozmnews.admin.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 운영자 프로필 원문 — 요청 본문이자 응답 — docs/specs/admin.md §3
 */
record ProfileText(@NotNull String profile) {

}
