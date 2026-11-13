package net.yozmnews.user.adapter.in.web;

import java.util.List;

import net.yozmnews.user.application.NoticeInbox;
import org.jspecify.annotations.Nullable;

/**
 * 알림 한 쪽 — docs/specs/user.md §3 · user-R27
 *
 * @param notices 최근부터
 * @param nextBefore 다음 쪽을 읽을 때 {@code before} 로 줄 값 — 다음 쪽이 없으면 null
 */
record NoticePageResponse(List<NoticeResponse> notices, @Nullable Long nextBefore) {

	static NoticePageResponse of(NoticeInbox.NoticePage page) {
		return new NoticePageResponse(page.notices().stream().map(NoticeResponse::of).toList(), page.nextBefore());
	}

}
