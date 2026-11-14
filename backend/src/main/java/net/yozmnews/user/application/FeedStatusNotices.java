package net.yozmnews.user.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.FeedLabels;
import net.yozmnews.source.FeedStatusTransition;
import net.yozmnews.source.FeedStatusesChanged;
import net.yozmnews.user.domain.notice.FeedStatusContent;
import net.yozmnews.user.domain.notice.FeedStatusReceipt;
import net.yozmnews.user.domain.notice.KeyInvalidatedContent;
import net.yozmnews.user.domain.notice.Notice;
import net.yozmnews.user.domain.notice.NoticeKind;
import net.yozmnews.user.domain.notice.NoticeRecipients;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.PasswordChangedContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 피드 상태 알림 유스케이스 — 검사 회차 하나의 전이를 지금 쓰는 운영자 계정마다 안 읽은 피드 상태 알림에 모은다. docs/specs/user.md §4 ·
 * user-R22 ~ user-R24
 * <p>
 * 한 회차가 트랜잭션 하나다 — 받는 사람 전부의 반영이 함께 커밋되거나 함께 롤백된다. 받는 사람마다 반영 기준 행을 계정 ID 순으로 잠그고,
 * 기준보다 늦은 회차만 반영한다(같은 회차의 두 번째 처리 · 늦게 온 앞선 회차는 거른다).
 */
@Service
public class FeedStatusNotices {

	private final FeedLabels labels;

	private final NoticeRecipients recipients;

	private final NoticeRepository notices;

	private final InstantSource clock;

	@Autowired
	FeedStatusNotices(FeedLabels labels, NoticeRecipients recipients, NoticeRepository notices) {
		this(labels, recipients, notices, InstantSource.system());
	}

	FeedStatusNotices(FeedLabels labels, NoticeRecipients recipients, NoticeRepository notices, InstantSource clock) {
		this.labels = Objects.requireNonNull(labels, "labels");
		this.recipients = Objects.requireNonNull(recipients, "recipients");
		this.notices = Objects.requireNonNull(notices, "notices");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** 회차 하나의 전이를 반영한다 */
	@Transactional
	public void record(FeedStatusesChanged changed) {
		Map<FeedId, FeedLabel> labels = this.labels
			.labels(changed.transitions().stream().map(FeedStatusTransition::feedId).toList());
		List<UserId> recipients = this.recipients.activeAdmins();
		Instant now = this.clock.instant();
		for (UserId user : recipients) {
			Optional<Instant> receivedThrough = this.notices.lockFeedStatusReceipt(user);
			if (!FeedStatusReceipt.accepts(receivedThrough.orElse(null), changed.judgedAt())) {
				continue;
			}
			Optional<Notice> unread = this.notices.lockUnread(user, NoticeKind.FEED_STATUS);
			if (unread.isPresent()) {
				FeedStatusContent content = switch (unread.get().content()) {
					case FeedStatusContent feedStatus -> feedStatus;
					// 잠근 것은 피드 상태 종류의 알림이다
					case PasswordChangedContent other -> throw new IllegalStateException("피드 상태 알림이 아니다");
					case KeyInvalidatedContent other -> throw new IllegalStateException("피드 상태 알림이 아니다");
				};
				this.notices.revise(unread.get().id(), content.merge(changed, labels), now);
			}
			else {
				this.notices.create(user, FeedStatusContent.first(changed, labels), now);
			}
			this.notices.saveFeedStatusReceipt(user, changed.judgedAt());
		}
	}

}
