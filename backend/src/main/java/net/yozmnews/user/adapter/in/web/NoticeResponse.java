package net.yozmnews.user.adapter.in.web;

import java.time.Instant;
import java.util.Map;

import net.yozmnews.user.domain.notice.DigestFailedContent;
import net.yozmnews.user.domain.notice.FeedStatusContent;
import net.yozmnews.user.domain.notice.KeyInvalidatedContent;
import net.yozmnews.user.domain.notice.Notice;
import net.yozmnews.user.domain.notice.NoticeKind;
import net.yozmnews.user.domain.notice.PasswordChangedContent;
import net.yozmnews.user.domain.notice.PreferencesAdjustedContent;
import net.yozmnews.user.domain.notice.ReportFailedContent;
import net.yozmnews.user.domain.notice.StorylineSubscriptionsRemovedContent;
import org.jspecify.annotations.Nullable;

/**
 * 알림 하나 — 판별자 {@code kind} 와 종류마다의 본문 하나(그 종류가 아니면 null). docs/specs/user.md §3 · user-R21 · user-R25 ·
 * user-R36
 *
 * @param id 알림 ID
 * @param kind 종류
 * @param revision 판 — 읽음 요청에 그대로 돌려준다
 * @param createdAt 만든 시각
 * @param updatedAt 내용을 마지막으로 고친 시각
 * @param read 읽었는가
 * @param feedStatus 피드 상태 알림의 본문 — 종류가 {@code FEED_STATUS} 일 때만
 * @param passwordChanged 비밀번호 알림의 본문 — 종류가 {@code PASSWORD_CHANGED} 일 때만
 * @param keyInvalidated 키 무효 알림의 본문 — 종류가 {@code KEY_INVALIDATED} 일 때만
 * @param preferencesAdjusted 선호 조정 알림의 본문 — 종류가 {@code PREFERENCES_ADJUSTED} 일 때만
 * @param storylineSubscriptionsRemoved 사라진 구독 알림의 본문 — 종류가 {@code STORYLINE_SUBSCRIPTIONS_REMOVED} 일 때만
 * @param digestFailed 다이제스트 실패 알림의 본문 — 종류가 {@code DIGEST_FAILED} 일 때만
 * @param reportFailed 리포트 실패 알림의 본문 — 종류가 {@code REPORT_FAILED} 일 때만
 */
record NoticeResponse(long id, NoticeKind kind, int revision, Instant createdAt, Instant updatedAt, boolean read,
		@Nullable FeedStatusNoticeResponse feedStatus, @Nullable PasswordChangedNoticeResponse passwordChanged,
		@Nullable KeyInvalidatedNoticeResponse keyInvalidated,
		@Nullable PreferencesAdjustedNoticeResponse preferencesAdjusted,
		@Nullable StorylineSubscriptionsRemovedNoticeResponse storylineSubscriptionsRemoved,
		@Nullable DigestFailedNoticeResponse digestFailed, @Nullable ReportFailedNoticeResponse reportFailed) {

	/** @param eventTitles 사라진 구독 알림이 가리키는 사건 ID → 지금 대표 기사 제목 */
	static NoticeResponse of(Notice notice, Map<Long, String> eventTitles) {
		return switch (notice.content()) {
			case FeedStatusContent content -> new NoticeResponse(notice.id(), notice.kind(), notice.revision(),
					notice.createdAt(), notice.updatedAt(), notice.read(), FeedStatusNoticeResponse.of(content), null, null, null,
					null, null, null);
			case PasswordChangedContent content -> new NoticeResponse(notice.id(), notice.kind(), notice.revision(),
					notice.createdAt(), notice.updatedAt(), notice.read(), null, PasswordChangedNoticeResponse.of(content), null, null,
					null, null, null);
			case KeyInvalidatedContent content -> new NoticeResponse(notice.id(), notice.kind(), notice.revision(),
					notice.createdAt(), notice.updatedAt(), notice.read(), null, null,
					KeyInvalidatedNoticeResponse.of(content), null, null, null, null);
			case PreferencesAdjustedContent content -> new NoticeResponse(notice.id(), notice.kind(), notice.revision(),
					notice.createdAt(), notice.updatedAt(), notice.read(), null, null, null,
					PreferencesAdjustedNoticeResponse.of(content), null, null, null);
			case StorylineSubscriptionsRemovedContent content -> new NoticeResponse(notice.id(), notice.kind(),
					notice.revision(), notice.createdAt(), notice.updatedAt(), notice.read(), null, null, null, null,
					StorylineSubscriptionsRemovedNoticeResponse.of(content, eventTitles), null, null);
			case DigestFailedContent content -> new NoticeResponse(notice.id(), notice.kind(), notice.revision(),
					notice.createdAt(), notice.updatedAt(), notice.read(), null, null, null, null, null,
					DigestFailedNoticeResponse.of(content), null);
			case ReportFailedContent content -> new NoticeResponse(notice.id(), notice.kind(), notice.revision(),
					notice.createdAt(), notice.updatedAt(), notice.read(), null, null, null, null, null, null,
					ReportFailedNoticeResponse.of(content));
		};
	}

}
