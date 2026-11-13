package net.yozmnews.user.adapter.in.web;

/**
 * 안 읽은 알림 수 — docs/specs/user.md §3
 *
 * @param count 안 읽은 알림 수
 */
record UnreadNoticesResponse(int count) {
}
