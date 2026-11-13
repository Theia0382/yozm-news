package net.yozmnews.user.adapter.in.web;

import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import net.yozmnews.platform.WebRole;
import net.yozmnews.user.UserId;
import net.yozmnews.user.application.NoticeInbox;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림함 — 세션 사용자의 알림 목록 · 안 읽은 수 · 읽음 처리. docs/specs/user.md §3 · user-R25 · user-R27
 */
@WebRole
@RestController
class NoticeController {

	private final NoticeInbox inbox;

	NoticeController(NoticeInbox inbox) {
		this.inbox = Objects.requireNonNull(inbox, "inbox");
	}

	@GetMapping("/api/notices")
	NoticePageResponse notices(UserId user, @RequestParam(required = false) @Positive @Nullable Long before) {
		return NoticePageResponse.of(this.inbox.page(user, before));
	}

	@GetMapping("/api/notices/unread-count")
	UnreadNoticesResponse unreadCount(UserId user) {
		return new UnreadNoticesResponse(this.inbox.unreadCount(user));
	}

	@PostMapping("/api/notices/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void read(UserId user, @Valid @RequestBody ReadNoticesRequest body) {
		this.inbox.markRead(user, body.seen());
	}

}
