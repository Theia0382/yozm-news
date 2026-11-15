package net.yozmnews.user.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import net.yozmnews.llm.Provider;

/**
 * 모델 고르기. 칸이 없거나 공급자 이름이 틀리면 본문 형식 400 이다. docs/specs/user.md §3 · user-R51
 *
 * @param provider 공급자
 * @param modelId 공급자의 모델 ID
 */
record InterestModelRequest(@NotNull Provider provider, @NotNull String modelId) {
}
