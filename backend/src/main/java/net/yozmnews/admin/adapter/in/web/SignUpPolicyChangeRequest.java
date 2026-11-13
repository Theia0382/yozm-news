package net.yozmnews.admin.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * 가입 정책 변경 요청 — 칸이 없으면 본문 형식 400 이다. docs/specs/admin.md §3
 *
 * @param open 공개 가입을 여는가
 */
record SignUpPolicyChangeRequest(@NotNull Boolean open) {
}
