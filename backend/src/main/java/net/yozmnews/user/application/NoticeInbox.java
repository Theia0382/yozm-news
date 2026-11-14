package net.yozmnews.user.application;

import java.time.InstantSource;
import java.util.List;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.notice.Notice;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.NoticeRetention;
import net.yozmnews.user.domain.notice.SeenNotice;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림함 — 세션 사용자의 알림 목록 · 안 읽은 수 · 읽음 처리. 모든 조회와 쓰기가 그 사용자로 범위가 정해진다. docs/specs/user.md §4 ·
 * user-R25 · user-R27
 */
@Service
public class NoticeInbox {

	private final NoticeRepository notices;

	private final InstantSource clock;

	@Autowired
	NoticeInbox(NoticeRepository notices) {
		this(notices, InstantSource.system());
	}

	NoticeInbox(NoticeRepository notices, InstantSource clock) {
		this.notices = Objects.requireNonNull(notices, "notices");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 알림 한 쪽 — 최근부터 {@link NoticeRetention#PAGE_SIZE} 개.
	 * @param before 이 ID 보다 작은 알림부터 — null 이면 처음부터
	 */
	@Transactional(readOnly = true)
	public NoticePage page(UserId user, @Nullable Long before) {
		List<Notice> read = this.notices.page(user, before, NoticeRetention.PAGE_SIZE + 1);
		if (read.size() <= NoticeRetention.PAGE_SIZE) {
			return new NoticePage(read, null);
		}
		List<Notice> page = read.subList(0, NoticeRetention.PAGE_SIZE);
		return new NoticePage(page, page.get(page.size() - 1).id());
	}

	/** 안 읽은 알림 수 */
	@Transactional(readOnly = true)
	public int unreadCount(UserId user) {
		return this.notices.countUnread(user);
	}

	/**
	 * 화면이 받은 판이 그대로이고 아직 읽지 않은 이 사용자의 알림만 읽음으로 바꾼다 — 다른 사용자의 알림 · 없는 알림 · 그사이 고쳐진 알림은 그대로다.
	 * @return 읽음으로 바꾼 수
	 */
	@Transactional
	public int markRead(UserId user, List<SeenNotice> seen) {
		return this.notices.markRead(user, seen, this.clock.instant());
	}

	/**
	 * 알림 한 쪽.
	 *
	 * @param notices 최근부터
	 * @param nextBefore 다음 쪽의 커서 — 이 쪽의 마지막 알림 ID. 다음 쪽이 없으면 null
	 */
	public record NoticePage(List<Notice> notices, @Nullable Long nextBefore) {

		public NoticePage {
			notices = List.copyOf(notices);
		}

	}

}
