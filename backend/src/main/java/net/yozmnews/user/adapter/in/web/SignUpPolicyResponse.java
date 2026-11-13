package net.yozmnews.user.adapter.in.web;

/**
 * 가입 정책 — 로그인 · 가입 화면이 가입 링크를 보일지 정한다. docs/specs/user.md §3
 *
 * @param open 공개 가입이 열렸는가
 */
record SignUpPolicyResponse(boolean open) {
}
