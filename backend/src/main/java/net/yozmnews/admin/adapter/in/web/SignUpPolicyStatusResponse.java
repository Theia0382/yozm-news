package net.yozmnews.admin.adapter.in.web;

import java.util.List;

/**
 * 가입 정책의 지금 값과 이력 — docs/specs/admin.md §3
 *
 * @param open 공개 가입이 열렸는가
 * @param changes 이력 — 최근부터
 */
record SignUpPolicyStatusResponse(boolean open, List<SignUpPolicyHistoryResponse> changes) {
}
