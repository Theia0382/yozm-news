package net.yozmnews.user.adapter.in.web;

import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;

import net.yozmnews.platform.AllowedWithoutLlmKey;
import net.yozmnews.platform.UserId;
import net.yozmnews.platform.WebRole;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.application.StorylineSubscriptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 스토리라인 구독 — 내 구독 목록 · 구독 · 해제. docs/specs/user.md §3 · user-R52
 * <p>
 * 모두 세션 사용자의 것이고 키 없이도 연다 — 구독은 스토리라인 ID · 구독 시각 · 상태만 내고 기사 내용을 내지 않는다(user-R5).
 */
@WebRole
@RestController
class StorylineSubscriptionController {

	private static final String OPEN_WITHOUT_KEY = "스토리라인 구독은 내용을 내지 않는다";

	private final StorylineSubscriptions subscriptions;

	private final InstantSource clock;

	@Autowired
	StorylineSubscriptionController(StorylineSubscriptions subscriptions) {
		this(subscriptions, InstantSource.system());
	}

	StorylineSubscriptionController(StorylineSubscriptions subscriptions, InstantSource clock) {
		this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** 내 구독 */
	@AllowedWithoutLlmKey(reason = OPEN_WITHOUT_KEY)
	@GetMapping("/api/storyline-subscriptions")
	StorylineSubscriptionsResponse subscriptions(UserId user) {
		return StorylineSubscriptionsResponse.of(this.subscriptions.view(user, now()));
	}

	/** 구독 — 스토리라인 ID 가 1 보다 작으면 400 */
	@AllowedWithoutLlmKey(reason = OPEN_WITHOUT_KEY)
	@PutMapping("/api/storyline-subscriptions/{storylineId}")
	StorylineSubscribeResponse subscribe(UserId user, @PathVariable long storylineId) {
		return StorylineSubscribeResponse.of(this.subscriptions.subscribe(user, storyline(storylineId), now()));
	}

	/** 해제 — 스토리라인 ID 가 1 보다 작으면 400 */
	@AllowedWithoutLlmKey(reason = OPEN_WITHOUT_KEY)
	@DeleteMapping("/api/storyline-subscriptions/{storylineId}")
	StorylineUnsubscribeResponse unsubscribe(UserId user, @PathVariable long storylineId) {
		return StorylineUnsubscribeResponse.of(this.subscriptions.unsubscribe(user, storyline(storylineId), now()));
	}

	private static StorylineId storyline(long storylineId) {
		if (storylineId < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "스토리라인 ID 는 1 이상이다");
		}
		return new StorylineId(storylineId);
	}

	private Instant now() {
		return this.clock.instant();
	}

}
