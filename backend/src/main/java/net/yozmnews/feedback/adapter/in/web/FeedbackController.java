package net.yozmnews.feedback.adapter.in.web;

import java.time.Instant;
import java.time.InstantSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import net.yozmnews.article.ArticleId;
import net.yozmnews.feedback.application.FeedbackRecording;
import net.yozmnews.feedback.application.FeedbackStates;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.GuardedAction;
import net.yozmnews.platform.RequestLimited;
import net.yozmnews.platform.UserId;
import net.yozmnews.platform.WebRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 좋아요 / 싫어요 — 누르기 · 되돌리기와 현재 상태. docs/specs/feedback.md §3 · feedback-R9
 * <p>
 * 둘 다 세션 사용자의 것이고 키 관문 뒤다(키 없이 여는 표시가 없다). 누르기 · 되돌리기에만 요청 제한이 걸린다.
 */
@WebRole
@RestController
class FeedbackController {

	private final FeedbackRecording recording;

	private final FeedbackStates states;

	private final InstantSource clock;

	FeedbackController(FeedbackRecording recording, FeedbackStates states, InstantSource clock) {
		this.recording = Objects.requireNonNull(recording, "recording");
		this.states = Objects.requireNonNull(states, "states");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** 누르기 · 되돌리기 — 기사 ID · 다이제스트 ID 가 1 보다 작으면 400 */
	@RequestLimited(GuardedAction.FEEDBACK)
	@PutMapping("/api/feedback/{articleId}")
	FeedbackResponse record(UserId user, @PathVariable long articleId, @Valid @RequestBody FeedbackRequest request) {
		long digestId = request.digestId();
		if (digestId < 1) {
			throw badRequest("다이제스트 ID 는 1 이상이다: " + digestId);
		}
		return FeedbackResponse
			.of(this.recording.record(user, article(articleId), request.value(), digestId, this.clock.instant()));
	}

	/** 현재 상태 — 물은 기사 가운데 세션 사용자의 현재 상태가 있는 것 */
	@GetMapping("/api/feedback")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "articleIds 가 비었거나 너무 많거나 1 미만 ID 가 있다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	FeedbackStatesResponse states(UserId user,
			@Parameter(description = "기사 ID — 쉼표로 이은 것, 서로 다른 ID 는 " + FeedbackStates.MAX_ARTICLES
					+ "개까지") @RequestParam List<Long> articleIds) {
		if (articleIds.isEmpty()) {
			throw badRequest("articleIds 가 비었다");
		}
		Set<ArticleId> articles = new LinkedHashSet<>();
		for (Long id : articleIds) {
			if (id == null || id < 1) {
				throw badRequest("기사 ID 는 1 이상이다: " + id);
			}
			articles.add(new ArticleId(id));
		}
		if (articles.size() > FeedbackStates.MAX_ARTICLES) {
			throw badRequest("서로 다른 기사는 " + FeedbackStates.MAX_ARTICLES + "개까지다: " + articles.size());
		}
		return FeedbackStatesResponse.of(this.states.among(user, articles));
	}

	private static ArticleId article(long articleId) {
		if (articleId < 1) {
			throw badRequest("기사 ID 는 1 이상이다: " + articleId);
		}
		return new ArticleId(articleId);
	}

	private static ResponseStatusException badRequest(String detail) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
	}

}
