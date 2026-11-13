package net.yozmnews.user.adapter.in.web;

/**
 * 초대 상태 — 링크를 연 사람의 화면이 가입 칸을 보일지 정한다. docs/specs/user.md §3
 *
 * @param state 초대 상태 — 읽을 수 없거나 없는 토큰이면 {@code UNKNOWN}
 */
record InvitationCheckResponse(InvitationStatus state) {
}
