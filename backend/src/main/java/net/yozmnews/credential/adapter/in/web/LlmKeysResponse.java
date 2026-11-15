package net.yozmnews.credential.adapter.in.web;

import java.util.List;

/**
 * 세션 사용자의 키 목록 — 공급자 선언 순. docs/specs/credential.md §3 · credential-R17
 *
 * @param providers 공급자마다 하나
 */
record LlmKeysResponse(List<ProviderKeyResponse> providers) {
}
