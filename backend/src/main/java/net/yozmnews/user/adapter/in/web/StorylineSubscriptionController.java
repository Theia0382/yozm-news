package net.yozmnews.user.adapter.in.web;

import java.time.Instant;
import java.time.InstantSource;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.platform.AllowedWithoutLlmKey;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.UserId;
import net.yozmnews.platform.WebRole;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.application.StorylineSubscriptionHistory;
import net.yozmnews.user.application.StorylineSubscriptions;
import net.yozmnews.user.domain.subscription.SubscriptionChangeCursor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 스토리라인 구독(화면 이름 "흐름 추적") — 추적 중인 흐름 목록 · 추적 상태 · 추적 · 해제 · 추적 이력. docs/specs/user.md §3 · user-R52 ·
 * user-R63 · user-R67
 * <p>
 * 모두 세션 사용자의 것이고 키 없이도 연다 — 스토리라인 ID · 시각 · 상태 · 더함 뺌만 내고 기사 내용을 내지 않는다(user-R5).
 */
@WebRole
@RestController
class StorylineSubscriptionController {

	private static final String OPEN_WITHOUT_KEY = "스토리라인 구독은 내용을 내지 않는다";

	private final StorylineSubscriptions subscriptions;

	private final StorylineSubscriptionHistory history;

	private final InstantSource clock;

	StorylineSubscriptionController(StorylineSubscriptions subscriptions, StorylineSubscriptionHistory history,
			InstantSource clock) {
		this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
		this.history = Objects.requireNonNull(history, "history");
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

	/** 흐름 추적 상태 — 받은 스토리라인마다 그 흐름을 추적 중인가. 없는 스토리라인은 추적 중이 아니다 */
	@AllowedWithoutLlmKey(reason = OPEN_WITHOUT_KEY)
	@GetMapping("/api/storyline-subscriptions/states")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "ids 가 비었거나 너무 많거나 1 미만 ID 가 있다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	StorylineTrackingStatesResponse states(UserId user,
			@Parameter(description = "스토리라인 ID — 쉼표로 이은 1 ~ 100개(같은 ID 포함)") @RequestParam List<Long> ids) {
		return StorylineTrackingStatesResponse.of(this.subscriptions.states(user, storylines(ids), now()));
	}

	/** 내 구독 이력 한 쪽 — 커서 두 값은 함께 오거나 함께 없다 */
	@AllowedWithoutLlmKey(reason = OPEN_WITHOUT_KEY)
	@GetMapping("/api/storyline-subscriptions/changes")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "다음 쪽 커서가 틀렸다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	StorylineSubscriptionChangesResponse changes(UserId user,
			@Parameter(description = "다음 쪽 커서의 시각(ISO-8601) — beforeId 와 함께") @RequestParam(
					required = false) @Nullable String beforeAt,
			@Parameter(description = "다음 쪽 커서의 이력 행 ID — beforeAt 과 함께") @RequestParam(
					required = false) @Nullable Long beforeId) {
		return StorylineSubscriptionChangesResponse.of(this.history.page(user, cursor(beforeAt, beforeId)));
	}

	/**
	 * 다음 쪽 커서 — 둘 다 없으면 첫 쪽.
	 * @throws ResponseStatusException 400 — 하나만 오거나, 시각 형식이 틀렸거나, ID 가 1 미만일 때
	 */
	private static @Nullable SubscriptionChangeCursor cursor(@Nullable String beforeAt, @Nullable Long beforeId) {
		if ((beforeAt == null) != (beforeId == null)) {
			throw badRequest("beforeAt 과 beforeId 는 함께 준다");
		}
		if (beforeAt == null) {
			return null;
		}
		long id = Objects.requireNonNull(beforeId);
		if (id < 1) {
			throw badRequest("이력 행 ID 는 1 이상이다: " + id);
		}
		try {
			return new SubscriptionChangeCursor(Instant.parse(beforeAt), id);
		}
		catch (DateTimeParseException ex) {
			throw badRequest("시각은 ISO-8601 형식이다: " + beforeAt);
		}
	}

	private static ResponseStatusException badRequest(String detail) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
	}

	/** 받은 값이 1 ~ {@link StorylineDirectory#MAX_PAGE_SIZE} 개(같은 ID 포함)이고 모두 1 이상이어야 한다 — 같은 ID 는 처음 자리 한 번 */
	private static List<StorylineId> storylines(List<Long> ids) {
		if (ids.isEmpty() || ids.size() > StorylineDirectory.MAX_PAGE_SIZE) {
			throw badRequest("ids 는 1 ~ " + StorylineDirectory.MAX_PAGE_SIZE + "개다: " + ids.size());
		}
		Set<StorylineId> distinct = new LinkedHashSet<>();
		for (Long id : ids) {
			if (id == null || id < 1) {
				throw badRequest("스토리라인 ID 는 1 이상이다: " + id);
			}
			distinct.add(new StorylineId(id));
		}
		return List.copyOf(distinct);
	}

	private static StorylineId storyline(long storylineId) {
		if (storylineId < 1) {
			throw badRequest("스토리라인 ID 는 1 이상이다");
		}
		return new StorylineId(storylineId);
	}

	private Instant now() {
		return this.clock.instant();
	}

}
