package net.yozmnews.user.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import net.yozmnews.llm.ReasoningEffort;

/**
 * 추론 강도 저장. 칸이 없거나 값 이름이 틀리면 본문 형식 400 이다. docs/specs/user.md §3 · user-R59
 *
 * @param effort 다이제스트 · 변화 리포트 생성의 추론 강도
 */
record InterestEffortRequest(@NotNull ReasoningEffort effort) {
}
