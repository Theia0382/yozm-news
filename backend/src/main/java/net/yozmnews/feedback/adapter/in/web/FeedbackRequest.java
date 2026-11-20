package net.yozmnews.feedback.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import net.yozmnews.feedback.FeedbackValue;

/**
 * 누르기 · 되돌리기 — 칸이 없거나 값 이름이 틀리면 본문 형식 400 이다. docs/specs/feedback.md §3 · feedback-R9
 *
 * @param value 남길 값 — 없음({@code NONE})은 되돌리기
 * @param digestId 누른 항목의 다이제스트 ID — 1 보다 작으면 400
 */
record FeedbackRequest(@NotNull FeedbackValue value, @NotNull Long digestId) {
}
