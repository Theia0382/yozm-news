package net.yozmnews.user.domain.notice;

/**
 * 알림 내용 — 종류마다 정한 레코드 하나. docs/specs/user.md §4 · user-R21
 */
public sealed interface NoticeContent permits FeedStatusContent, PasswordChangedContent, KeyInvalidatedContent,
		PreferencesAdjustedContent, StorylineSubscriptionsRemovedContent, DigestFailedContent {

	/** 이 내용의 종류 */
	default NoticeKind kind() {
		return switch (this) {
			case FeedStatusContent content -> NoticeKind.FEED_STATUS;
			case PasswordChangedContent content -> NoticeKind.PASSWORD_CHANGED;
			case KeyInvalidatedContent content -> NoticeKind.KEY_INVALIDATED;
			case PreferencesAdjustedContent content -> NoticeKind.PREFERENCES_ADJUSTED;
			case StorylineSubscriptionsRemovedContent content -> NoticeKind.STORYLINE_SUBSCRIPTIONS_REMOVED;
			case DigestFailedContent content -> NoticeKind.DIGEST_FAILED;
		};
	}

}
