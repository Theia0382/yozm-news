package net.yozmnews.reader.adapter.in.web;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.digest.DigestDirectory;
import net.yozmnews.digest.DigestItemCursor;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.UserId;
import net.yozmnews.platform.WebRole;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineView;
import net.yozmnews.user.UserStorylineId;
import net.yozmnews.user.UserStorylineSummaries;
import net.yozmnews.user.UserStorylineSummaryView;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 독자 화면의 정리된 사용자 스토리라인(화면 이름 "사건 흐름") — 목록 · 하나 · 그 사용자 스토리라인에 묶인 지난 다이제스트 항목. docs/specs/reader.md §3
 * <p>
 * {@code user} 의 정리 조회, {@code storyline} 의 스토리라인 조회, {@code digest} 의 항목 조회만 부르고 응답 모양을 정한다 — 무엇이 정리된
 * 사용자 스토리라인인지는 {@code user} 의 정리 작업이 정한다. 셋 모두 세션이 필요하고 키 관문 뒤다(키 없이 여는 표시 · 요청 방어 표시가 없다). 세션 사용자의
 * 것만 낸다 — 다른 사용자의 사용자 스토리라인 ID 는 없는 것이다. 트랜잭션을 열지 않는다.
 */
@WebRole
@RestController
class UserStorylineScreenController {

	/** 지난 다이제스트 항목 한 쪽의 수 — {@link DigestDirectory#MAX_ITEMS} 이하. 측정 전 목표 */
	static final int ITEMS_PAGE_SIZE = 20;

	private final UserStorylineSummaries summaries;

	private final StorylineDirectory storylines;

	private final DigestDirectory digests;

	UserStorylineScreenController(UserStorylineSummaries summaries, StorylineDirectory storylines,
			DigestDirectory digests) {
		this.summaries = Objects.requireNonNull(summaries, "summaries");
		this.storylines = Objects.requireNonNull(storylines, "storylines");
		this.digests = Objects.requireNonNull(digests, "digests");
	}

	@GetMapping("/api/user-storylines")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	UserStorylineResponses.ListResponse userStorylines(UserId user) {
		return new UserStorylineResponses.ListResponse(
				this.summaries.shown(user).stream().map(UserStorylineResponses.CardResponse::of).toList());
	}

	@GetMapping("/api/user-storylines/{id}")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "404", description = "없는 사용자 스토리라인이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	UserStorylineResponses.DetailResponse userStoryline(UserId user,
			@Parameter(description = "사용자 스토리라인 ID") @PathVariable long id) {
		UserStorylineSummaryView view = find(user, id);
		Map<StorylineId, StorylineView> tops = this.storylines.findAll(view.members());
		return UserStorylineResponses.DetailResponse.of(view, tops);
	}

	@GetMapping("/api/user-storylines/{id}/items")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "커서가 틀렸다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "404", description = "없는 사용자 스토리라인이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	UserStorylineResponses.ItemsResponse userStorylineItems(UserId user,
			@Parameter(description = "사용자 스토리라인 ID") @PathVariable long id,
			@Parameter(description = "다음 쪽 커서의 날짜(ISO-8601, YYYY-MM-DD) — beforePosition 과 함께") @RequestParam(
					required = false) @Nullable String beforeDate,
			@Parameter(description = "다음 쪽 커서의 섹션 위치(0 이상) — beforeDate 와 함께") @RequestParam(
					required = false) @Nullable Integer beforePosition) {
		DigestItemCursor before = ScreenRequests.itemCursor(beforeDate, beforePosition);
		UserStorylineSummaryView view = find(user, id);
		return UserStorylineResponses.ItemsResponse
			.of(this.digests.itemsOfStorylines(user, view.members(), before, ITEMS_PAGE_SIZE));
	}

	private UserStorylineSummaryView find(UserId user, long id) {
		if (id < 1) {
			throw ScreenRequests.unknownUserStoryline(id);
		}
		return this.summaries.find(user, new UserStorylineId(id))
			.orElseThrow(() -> ScreenRequests.unknownUserStoryline(id));
	}

}
