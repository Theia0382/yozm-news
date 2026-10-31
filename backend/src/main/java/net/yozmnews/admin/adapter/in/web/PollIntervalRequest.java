package net.yozmnews.admin.adapter.in.web;

import org.jspecify.annotations.Nullable;

/**
 * 운영자 수집 주기(분) — null 이면 설정을 지운다. docs/specs/admin.md §3
 */
record PollIntervalRequest(@Nullable Integer minutes) {

}
