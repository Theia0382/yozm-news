package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static net.yozmnews.admin.adapter.in.web.AdminRequests.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.yozmnews.platform.BatchResult;
import net.yozmnews.platform.DiscardOutcome;
import net.yozmnews.platform.DiscardResult;
import net.yozmnews.platform.ResubmissionOutcome;
import net.yozmnews.platform.ResubmissionResult;
import net.yozmnews.platform.StrandedPublication;
import net.yozmnews.platform.StrandedPublications;
import net.yozmnews.platform.StrandedReason;
import net.yozmnews.platform.application.WebSliceSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 남겨진 이벤트 발행 — 목록 응답 모양(UUID 문자열 ID · 까닭 · 실을 수 있는 건수 500 · null 키), 다시 처리 · 폐기의 요청 ID
 * 전달과 발행마다의 판별자, 공개 API 가 상한을 넘었다고 할 때 · 본문이 틀렸을 때의 400, 토큰 없음 401.
 * <p>
 * 지키는 규칙: admin-R1 · admin-R3 · platform-R14 · platform-R15
 */
@WebMvcTest(controllers = AdminEventPublicationController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeEventPublicationAdministration.Config.class })
class AdminEventPublicationControllerTests {

	private static final UUID FIRST = UUID.fromString("10000000-0000-0000-0000-000000000001");

	private static final UUID SECOND = UUID.fromString("20000000-0000-0000-0000-000000000002");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeEventPublicationAdministration administration;

	@BeforeEach
	void reset() {
		this.administration.reset();
	}

	@Test
	void listCarriesEachStrandedPublicationAndTheLimit() throws Exception {
		this.administration.stranded = new StrandedPublications(List.of(
				new StrandedPublication(FIRST, StrandedReason.EXHAUSTED, "net.yozmnews.source.ArticleFetched",
						"net.yozmnews.article.application.ArticleFetchedListener.on(net.yozmnews.source.ArticleFetched)",
						Instant.parse("2026-10-30T01:00:00Z"), 6, Instant.parse("2026-10-30T01:25:00Z")),
				new StrandedPublication(SECOND, StrandedReason.UNREADABLE, "net.yozmnews.source.RenamedEvent", "listener",
						Instant.parse("2026-10-31T02:00:00Z"), 1, null)),
				7);

		this.mockMvc.perform(authorized(get("/api/admin/event-publications")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"total":7,"limit":500,"publications":[
					 {"id":"10000000-0000-0000-0000-000000000001","reason":"EXHAUSTED",
					  "eventType":"net.yozmnews.source.ArticleFetched",
					  "listenerId":"net.yozmnews.article.application.ArticleFetchedListener.on(net.yozmnews.source.ArticleFetched)",
					  "publishedAt":"2026-10-30T01:00:00Z","completionAttempts":6,"lastResubmittedAt":"2026-10-30T01:25:00Z"},
					 {"id":"20000000-0000-0000-0000-000000000002","reason":"UNREADABLE",
					  "eventType":"net.yozmnews.source.RenamedEvent","listenerId":"listener",
					  "publishedAt":"2026-10-31T02:00:00Z","completionAttempts":1,"lastResubmittedAt":null}]}""",
					JsonCompareMode.STRICT));
	}

	@Test
	void resubmissionPassesTheIdsAndCarriesEachResult() throws Exception {
		this.administration.resubmission = new BatchResult.Processed<>(
				List.of(new ResubmissionResult(FIRST, ResubmissionOutcome.RESUBMITTED),
						new ResubmissionResult(SECOND, ResubmissionOutcome.UNREADABLE)));

		this.mockMvc
			.perform(json(post("/api/admin/event-publications/resubmission"),
					"{\"ids\":[\"20000000-0000-0000-0000-000000000002\",\"10000000-0000-0000-0000-000000000001\","
							+ "\"10000000-0000-0000-0000-000000000001\"]}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"results":[{"id":"10000000-0000-0000-0000-000000000001","result":"RESUBMITTED"},
					            {"id":"20000000-0000-0000-0000-000000000002","result":"UNREADABLE"}]}""",
					JsonCompareMode.STRICT));
		assertThat(this.administration.resubmitted).containsExactly(Set.of(FIRST, SECOND));
	}

	@Test
	void discardPassesTheIdsAndCarriesEachResult() throws Exception {
		this.administration.discard = new BatchResult.Processed<>(
				List.of(new DiscardResult(FIRST, DiscardOutcome.DISCARDED),
						new DiscardResult(SECOND, DiscardOutcome.CHANGED)));

		this.mockMvc
			.perform(json(post("/api/admin/event-publications/discard"),
					"{\"ids\":[\"10000000-0000-0000-0000-000000000001\",\"20000000-0000-0000-0000-000000000002\"]}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"results":[{"id":"10000000-0000-0000-0000-000000000001","result":"DISCARDED"},
					            {"id":"20000000-0000-0000-0000-000000000002","result":"CHANGED"}]}""",
					JsonCompareMode.STRICT));
		assertThat(this.administration.discarded).containsExactly(Set.of(FIRST, SECOND));
	}

	@Test
	void emptyIdsAreAnEmptyResultNotABadRequest() throws Exception {
		this.administration.resubmission = new BatchResult.Processed<>(List.of());
		this.administration.discard = new BatchResult.Processed<>(List.of());

		this.mockMvc.perform(json(post("/api/admin/event-publications/resubmission"), "{\"ids\":[]}"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"results\":[]}", JsonCompareMode.STRICT));
		this.mockMvc.perform(json(post("/api/admin/event-publications/discard"), "{\"ids\":[]}"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"results\":[]}", JsonCompareMode.STRICT));
		assertThat(this.administration.resubmitted).containsExactly(Set.of());
		assertThat(this.administration.discarded).containsExactly(Set.of());
	}

	@Test
	void overLimitFromTheAdministrationIsBadRequest() throws Exception {
		this.administration.resubmission = new BatchResult.OverLimit<>(500);
		this.administration.discard = new BatchResult.OverLimit<>(500);

		this.mockMvc
			.perform(json(post("/api/admin/event-publications/resubmission"),
					"{\"ids\":[\"10000000-0000-0000-0000-000000000001\"]}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Bad Request","status":400,"detail":"한 번에 500건까지 처리한다",
					 "instance":"/api/admin/event-publications/resubmission"}""", JsonCompareMode.STRICT));
		this.mockMvc
			.perform(json(post("/api/admin/event-publications/discard"),
					"{\"ids\":[\"10000000-0000-0000-0000-000000000001\"]}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Bad Request","status":400,"detail":"한 번에 500건까지 처리한다",
					 "instance":"/api/admin/event-publications/discard"}""", JsonCompareMode.STRICT));
	}

	@Test
	void malformedBodiesAreBadRequestAndCallNothing() throws Exception {
		for (String body : List.of("{\"ids\":[\"not-a-uuid\"]}", "{}", "{\"ids\":null}", "{\"ids\":[null]}")) {
			this.mockMvc.perform(json(post("/api/admin/event-publications/resubmission"), body))
				.andExpect(status().isBadRequest());
			this.mockMvc.perform(json(post("/api/admin/event-publications/discard"), body))
				.andExpect(status().isBadRequest());
		}
		assertThat(this.administration.resubmitted).isEmpty();
		assertThat(this.administration.discarded).isEmpty();
	}

	@Test
	void requestWithoutTheTokenIsUnauthorizedAndCallsNothing() throws Exception {
		this.mockMvc.perform(get("/api/admin/event-publications")).andExpect(status().isUnauthorized());
		this.mockMvc
			.perform(post("/api/admin/event-publications/resubmission").contentType("application/json")
				.content("{\"ids\":[]}"))
			.andExpect(status().isUnauthorized());
		this.mockMvc
			.perform(post("/api/admin/event-publications/discard").contentType("application/json")
				.content("{\"ids\":[]}"))
			.andExpect(status().isUnauthorized());
		assertThat(this.administration.resubmitted).isEmpty();
		assertThat(this.administration.discarded).isEmpty();
	}

}
