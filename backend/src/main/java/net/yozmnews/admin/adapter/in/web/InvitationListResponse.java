package net.yozmnews.admin.adapter.in.web;

import java.util.List;

/**
 * 최근에 발급한 초대 — 발급 순 역순. docs/specs/admin.md §3
 *
 * @param invitations 초대들
 */
record InvitationListResponse(List<InvitationRowResponse> invitations) {
}
