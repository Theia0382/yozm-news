package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import net.yozmnews.account.application.WebSliceSecurity;
import net.yozmnews.platform.RoundEnd;
import net.yozmnews.platform.RoundQueue;
import net.yozmnews.platform.RoundSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 처리 현황 — 모듈 셋의 회차를 사건 · 흐름 · 태그 순으로 이어 붙인 응답 모양, 기록 없는 회차의 마지막 회차 null, 토큰 없음 401.
 */
@WebMvcTest(controllers = AdminProcessingController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeProcessing.Config.class })
class AdminProcessingControllerTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeProcessing processing;

	@BeforeEach
	void reset() {
		this.processing.reset();
	}

	@Test
	void roundsOfTheThreeModulesAreJoinedInOrderWithTheirLastRun() throws Exception {
		this.processing.events = List.of(new RoundQueue("event-features", 3, 0, RoundSummary.NONE),
				new RoundQueue("event-assignment", 40, 2,
						new RoundSummary(96, 3, new RoundSummary.LastRound(Instant.parse("2026-11-26T12:00:00Z"),
								Instant.parse("2026-11-26T12:05:02Z"), 11, 352, RoundEnd.BUDGET))));
		this.processing.storylines = List.of(new RoundQueue("storyline-linking", 0, 0,
				new RoundSummary(0, 1, new RoundSummary.LastRound(Instant.parse("2026-11-26T12:06:00Z"),
						Instant.parse("2026-11-26T12:06:00Z"), 1, 0, RoundEnd.DRAINED))));
		this.processing.tags = List.of(new RoundQueue("tag-evaluation", 5, 5, RoundSummary.NONE));

		this.mockMvc.perform(authorized(get("/api/admin/processing")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"rounds":[
					 {"round":"event-features","waiting":3,"deferred":0,"recentHandled":0,"recentRuns":0,"lastRun":null},
					 {"round":"event-assignment","waiting":40,"deferred":2,"recentHandled":96,"recentRuns":3,
					  "lastRun":{"startedAt":"2026-11-26T12:00:00Z","endedAt":"2026-11-26T12:05:02Z","batches":11,
					             "handled":352,"endedBy":"BUDGET"}},
					 {"round":"storyline-linking","waiting":0,"deferred":0,"recentHandled":0,"recentRuns":1,
					  "lastRun":{"startedAt":"2026-11-26T12:06:00Z","endedAt":"2026-11-26T12:06:00Z","batches":1,
					             "handled":0,"endedBy":"DRAINED"}},
					 {"round":"tag-evaluation","waiting":5,"deferred":5,"recentHandled":0,"recentRuns":0,"lastRun":null}
					]}""", JsonCompareMode.STRICT));
		assertThat(this.processing.called).containsExactly("event", "storyline", "tag");
	}

	@Test
	void requestWithoutTheTokenIsUnauthorizedAndCallsNothing() throws Exception {
		this.mockMvc.perform(get("/api/admin/processing")).andExpect(status().isUnauthorized());

		assertThat(this.processing.called).isEmpty();
	}

}
