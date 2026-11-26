package net.yozmnews.admin.adapter.in.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.yozmnews.event.EventProcessing;
import net.yozmnews.platform.RoundQueue;
import net.yozmnews.platform.WebRole;
import net.yozmnews.storyline.StorylineProcessing;
import net.yozmnews.tag.TagProcessing;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 API — 처리 현황. docs/specs/admin.md §3.
 * <p>
 * {@code event} · {@code storyline} · {@code tag} 의 처리 현황 조회를 이 순서로 불러 이어 붙이기만 한다 — 화면 표의 행 순서다. 트랜잭션을 열지
 * 않는다 — 조회의 트랜잭션은 각 모듈이 연다.
 */
@WebRole
@RestController
class AdminProcessingController {

	private final EventProcessing events;

	private final StorylineProcessing storylines;

	private final TagProcessing tags;

	AdminProcessingController(EventProcessing events, StorylineProcessing storylines, TagProcessing tags) {
		this.events = Objects.requireNonNull(events, "events");
		this.storylines = Objects.requireNonNull(storylines, "storylines");
		this.tags = Objects.requireNonNull(tags, "tags");
	}

	@GetMapping("/api/admin/processing")
	@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
	ProcessingResponse processingRounds() {
		List<RoundQueue> rounds = new ArrayList<>(this.events.rounds());
		rounds.addAll(this.storylines.rounds());
		rounds.addAll(this.tags.rounds());
		return ProcessingResponse.of(rounds);
	}

}
