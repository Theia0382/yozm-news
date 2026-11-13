package net.yozmnews.admin.adapter.in.web;

import org.jspecify.annotations.Nullable;

/**
 * 초대 발급 요청 — docs/specs/admin.md §3
 *
 * @param memo 운영자가 목록에서 가르는 메모 — 없거나 비면 없음
 */
record InvitationIssueRequest(@Nullable String memo) {
}
