package net.yozmnews.user.adapter.in.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import net.yozmnews.user.domain.notice.NoticeRetention;
import net.yozmnews.user.domain.notice.SeenNotice;

/**
 * 읽음 요청 — 화면이 받은 알림의 ID 와 판. 한 번에 알림함 한 쪽까지. docs/specs/user.md §3 · user-R25
 *
 * @param notices 받은 알림 — {@link NoticeRetention#PAGE_SIZE} 개까지
 */
record ReadNoticesRequest(
		@NotNull @Size(max = NoticeRetention.PAGE_SIZE) List<@NotNull @Valid NoticeRevision> notices) {

	List<SeenNotice> seen() {
		return this.notices.stream().map(notice -> new SeenNotice(notice.id(), notice.revision())).toList();
	}

	/**
	 * 받은 알림 하나 — 알림 ID 와 받은 판.
	 *
	 * @param id 알림 ID — 1 이상
	 * @param revision 받은 판 — 1 이상
	 */
	record NoticeRevision(@Positive long id, @Positive int revision) {
	}

}
