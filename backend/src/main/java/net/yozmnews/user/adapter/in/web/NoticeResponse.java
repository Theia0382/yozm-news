package net.yozmnews.user.adapter.in.web;

import java.time.Instant;

import net.yozmnews.user.domain.notice.FeedStatusContent;
import net.yozmnews.user.domain.notice.Notice;
import net.yozmnews.user.domain.notice.NoticeKind;
import net.yozmnews.user.domain.notice.PasswordChangedContent;
import org.jspecify.annotations.Nullable;

/**
 * 알림 하나 — 판별자 {@code kind} 와 종류마다의 본문 하나(그 종류가 아니면 null). docs/specs/user.md §3 · user-R21 · user-R25
 *
 * @param id 알림 ID
 * @param kind 종류
 * @param revision 판 — 읽음 요청에 그대로 돌려준다
 * @param createdAt 만든 시각
 * @param updatedAt 내용을 마지막으로 고친 시각
 * @param read 읽었는가
 * @param feedStatus 피드 상태 알림의 본문 — 종류가 {@code FEED_STATUS} 일 때만
 * @param passwordChanged 비밀번호 알림의 본문 — 종류가 {@code PASSWORD_CHANGED} 일 때만
 */
record NoticeResponse(long id, NoticeKind kind, int revision, Instant createdAt, Instant updatedAt, boolean read,
		@Nullable FeedStatusNoticeResponse feedStatus, @Nullable PasswordChangedNoticeResponse passwordChanged) {

	static NoticeResponse of(Notice notice) {
		return switch (notice.content()) {
			case FeedStatusContent content -> new NoticeResponse(notice.id(), notice.kind(), notice.revision(),
					notice.createdAt(), notice.updatedAt(), notice.read(), FeedStatusNoticeResponse.of(content), null);
			case PasswordChangedContent content -> new NoticeResponse(notice.id(), notice.kind(), notice.revision(),
					notice.createdAt(), notice.updatedAt(), notice.read(), null, PasswordChangedNoticeResponse.of(content));
		};
	}

}
