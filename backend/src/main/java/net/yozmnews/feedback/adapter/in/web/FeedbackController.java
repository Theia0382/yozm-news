package net.yozmnews.feedback.adapter.in.web;

import java.time.InstantSource;
import java.util.Objects;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import net.yozmnews.event.EventId;
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
 * 좋아요 / 싫어요 — 다이제스트 항목의 누르기 · 되돌리기와 항목 기록. docs/specs/feedback.md §3 · feedback-R9
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

	/** 누르기 · 되돌리기 — 항목의 사건 ID(항목 키) · 다이제스트 ID 가 1 보다 작으면 400 */
	@RequestLimited(GuardedAction.FEEDBACK)
	@PutMapping("/api/feedback/{eventId}")
	FeedbackResponse record(UserId user, @PathVariable long eventId, @Valid @RequestBody FeedbackRequest request) {
		if (eventId < 1) {
			throw badRequest("사건 ID 는 1 이상이다: " + eventId);
		}
		long digestId = request.digestId();
		if (digestId < 1) {
			throw badRequest("다이제스트 ID 는 1 이상이다: " + digestId);
		}
		return FeedbackResponse.of(
				this.recording.record(user, new EventId(eventId), request.value(), digestId, this.clock.instant()));
	}

	/** 항목 기록 — 그 다이제스트에서 세션 사용자가 값을 누른 항목 */
	@GetMapping("/api/feedback")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "digestId 가 없거나 1 미만이다",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	FeedbackItemsResponse items(UserId user,
			@Parameter(description = "다이제스트 ID") @RequestParam long digestId) {
		if (digestId < 1) {
			throw badRequest("다이제스트 ID 는 1 이상이다: " + digestId);
		}
		return FeedbackItemsResponse.of(this.states.itemsOf(user, digestId));
	}

	private static ResponseStatusException badRequest(String detail) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
	}

}
