package net.yozmnews.admin.adapter.in.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * 다시 처리 · 폐기할 이벤트 발행 ID — 요청 본문. docs/specs/admin.md §3
 */
record EventPublicationIdsRequest(@NotNull List<@NotNull UUID> ids) {

}
