package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static net.yozmnews.admin.adapter.in.web.AdminRequests.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import net.yozmnews.platform.application.WebSliceSecurity;
import net.yozmnews.source.ActivationRefusal;
import net.yozmnews.source.FailurePermanence;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedView;
import net.yozmnews.source.FetchFailureReason;
import net.yozmnews.source.FetchFailureView;
import net.yozmnews.source.ProfilePreviewResult;
import net.yozmnews.source.ProfileProblemView;
import net.yozmnews.source.ProfileRevisionResult;
import net.yozmnews.source.ProfileState;
import net.yozmnews.source.SourceEnableResult;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.SourceRegistrationResult;
import net.yozmnews.source.SourceView;
import net.yozmnews.source.UnknownSourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 소스 — 목록, 운영자 원문 등록 · 조회 · 수정 · 미리보기, 소스 켜기 · 끄기 · 수집 주기.
 * <p>
 * 지키는 규칙: 운영자 API(docs/specs/admin.md §5) · source-R1 · source-R4 · source-R9 · source-R14
 */
@WebMvcTest(controllers = AdminSourceController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeSourceAdministration.Config.class })
class AdminSourceControllerTests {

	private static final ProfileProblemView PROBLEM = new ProfileProblemView("feeds[0].url", "INVALID_URL",
			"http · https 절대 URL 이 아니다", 7, 10);

	private static final String PROBLEM_JSON = """
			{"path":"feeds[0].url","code":"INVALID_URL","message":"http · https 절대 URL 이 아니다","line":7,"column":10}
			""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeSourceAdministration administration;

	@BeforeEach
	void reset() {
		this.administration.reset();
	}

	@Test
	void listCarriesTheMinimumIntervalAndEachSourceWithItsFeeds() throws Exception {
		this.administration.sources = List.of(
				new SourceView(new SourceId(3), "multi", true, 30, ProfileState.AVAILABLE, "멀티일보",
						URI.create("https://multi.example"), LocalDate.parse("2026-10-20"),
						List.of(new FeedView(new FeedId(11), "one", true, null, true,
								URI.create("https://multi.example/rss/one.xml"), "하나", 30),
								new FeedView(new FeedId(12), "gone", false, 45, false, null, null, null))),
				new SourceView(new SourceId(4), null, false, null, ProfileState.INVALID, null, null, null, List.of()));

		this.mockMvc.perform(authorized(get("/api/admin/sources")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"minimumPollIntervalMinutes":15,"sources":[
					 {"id":3,"presetKey":"multi","enabled":true,"pollIntervalMinutes":30,"profileState":"AVAILABLE",
					  "name":"멀티일보","homepage":"https://multi.example","termsReviewedAt":"2026-10-20","feeds":[
					   {"id":11,"feedKey":"one","enabled":true,"pollIntervalMinutes":null,"inProfile":true,
					    "url":"https://multi.example/rss/one.xml","section":"하나","effectivePollIntervalMinutes":30},
					   {"id":12,"feedKey":"gone","enabled":false,"pollIntervalMinutes":45,"inProfile":false,
					    "url":null,"section":null,"effectivePollIntervalMinutes":null}]},
					 {"id":4,"presetKey":null,"enabled":false,"pollIntervalMinutes":null,"profileState":"INVALID",
					  "name":null,"homepage":null,"termsReviewedAt":null,"feeds":[]}]}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void registeringAnOperatorTextAnswersTheSourceOrItsProblems() throws Exception {
		this.administration.registration = new SourceRegistrationResult.Registered(new SourceId(5));
		this.mockMvc.perform(json(post("/api/admin/sources"), "{\"profile\":\"원문\"}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"REGISTERED","sourceId":5,"problems":[]}
					""", JsonCompareMode.STRICT));

		this.administration.registration = new SourceRegistrationResult.InvalidProfile(List.of(PROBLEM));
		this.mockMvc.perform(json(post("/api/admin/sources"), "{\"profile\":\"틀린 원문\"}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"INVALID_PROFILE","sourceId":null,"problems":[%s]}
					""".formatted(PROBLEM_JSON), JsonCompareMode.STRICT));

		assertThat(this.administration.calls).containsExactly("registerOperatorProfile 원문",
				"registerOperatorProfile 틀린 원문");
	}

	@Test
	void aBodyWithoutTheTextIsABadRequest() throws Exception {
		this.mockMvc.perform(json(post("/api/admin/sources"), "{}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		assertThat(this.administration.calls).isEmpty();
	}

	@Test
	void operatorTextIsReadOnlyFromOperatorSources() throws Exception {
		this.administration.profileText = Optional.of("source:\n  name: 운영자일보\n");
		this.mockMvc.perform(authorized(get("/api/admin/sources/5/profile")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"profile":"source:\\n  name: 운영자일보\\n"}
					""", JsonCompareMode.STRICT));

		this.administration.profileText = Optional.empty();
		this.mockMvc.perform(authorized(get("/api/admin/sources/3/profile")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Not Found","status":404,"detail":"프리셋을 참조하는 소스라 운영자 원문이 없다",
					 "instance":"/api/admin/sources/3/profile"}
					""", JsonCompareMode.STRICT));

		this.administration.failure = new UnknownSourceException(new SourceId(99));
		this.mockMvc.perform(authorized(get("/api/admin/sources/99/profile")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Not Found","status":404,"detail":"없는 소스: 99",
					 "instance":"/api/admin/sources/99/profile"}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void revisionAnswersTheChangedFeedsTheProblemsOrNotFoundForAPresetSource() throws Exception {
		this.administration.revision = new ProfileRevisionResult.Revised(List.of(new FeedId(11)), true,
				List.of(new FeedId(13), new FeedId(14)));
		this.mockMvc.perform(json(put("/api/admin/sources/5/profile"), "{\"profile\":\"새 원문\"}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"REVISED","disabledFeedIds":[11],"sourceDisabled":true,"addedFeedIds":[13,14],"problems":[]}
					""", JsonCompareMode.STRICT));

		this.administration.revision = new ProfileRevisionResult.InvalidProfile(List.of(PROBLEM));
		this.mockMvc.perform(json(put("/api/admin/sources/5/profile"), "{\"profile\":\"틀린 원문\"}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"INVALID_PROFILE","disabledFeedIds":[],"sourceDisabled":false,"addedFeedIds":[],
					 "problems":[%s]}
					""".formatted(PROBLEM_JSON), JsonCompareMode.STRICT));

		this.administration.revision = new ProfileRevisionResult.NotOperatorProfile();
		this.mockMvc.perform(json(put("/api/admin/sources/3/profile"), "{\"profile\":\"원문\"}"))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Not Found","status":404,"detail":"프리셋을 참조하는 소스라 운영자 원문이 없다",
					 "instance":"/api/admin/sources/3/profile"}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void revisionThatLostARaceIsAConflict() throws Exception {
		this.administration.failure = new OptimisticLockingFailureException("version");

		this.mockMvc.perform(json(put("/api/admin/sources/5/profile"), "{\"profile\":\"새 원문\"}"))
			.andExpect(status().isConflict())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Conflict","status":409,"detail":"다른 요청이 먼저 바꿨다 — 다시 읽고 시도한다",
					 "instance":"/api/admin/sources/5/profile"}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void previewAnswersEachOutcome() throws Exception {
		this.administration.preview = new ProfilePreviewResult.FeedNotInProfile("other");
		this.mockMvc.perform(json(post("/api/admin/profile-previews"), "{\"profile\":\"원문\",\"feedKey\":\"other\"}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"FEED_NOT_IN_PROFILE","problems":[],"preview":null,"shortfalls":[],"failure":null}
					""", JsonCompareMode.STRICT));

		this.administration.preview = new ProfilePreviewResult.InvalidProfile(List.of(PROBLEM));
		this.mockMvc.perform(json(post("/api/admin/profile-previews"), "{\"profile\":\"틀린\",\"feedKey\":\"main\"}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"INVALID_PROFILE","problems":[%s],"preview":null,"shortfalls":[],"failure":null}
					""".formatted(PROBLEM_JSON), JsonCompareMode.STRICT));

		this.administration.preview = new ProfilePreviewResult.Unfetched(new FetchFailureView(
				FetchFailureReason.BLOCKED_ADDRESS, FailurePermanence.PERMANENT, "내부 주소로 가는 요청을 막았다: 10.0.0.1", null,
				null));
		this.mockMvc.perform(json(post("/api/admin/profile-previews"), "{\"profile\":\"원문\",\"feedKey\":\"main\"}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"UNFETCHED","problems":[],"preview":null,"shortfalls":[],
					 "failure":{"reason":"BLOCKED_ADDRESS","permanence":"PERMANENT",
					            "message":"내부 주소로 가는 요청을 막았다: 10.0.0.1","httpStatus":null,"problem":null}}
					""", JsonCompareMode.STRICT));

		assertThat(this.administration.calls).containsExactly("previewOperatorProfile other 원문",
				"previewOperatorProfile main 틀린", "previewOperatorProfile main 원문");
	}

	@Test
	void enablingASourceAnswersTheRefusalAndDisablingAnswersNoContent() throws Exception {
		this.administration.sourceEnable = new SourceEnableResult.Refused(ActivationRefusal.TERMS_NOT_REVIEWED);
		this.mockMvc.perform(authorized(post("/api/admin/sources/5/enable")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"REFUSED","refusal":"TERMS_NOT_REVIEWED"}
					""", JsonCompareMode.STRICT));

		this.administration.sourceEnable = new SourceEnableResult.Enabled();
		this.mockMvc.perform(authorized(post("/api/admin/sources/5/enable")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"ENABLED","refusal":null}
					""", JsonCompareMode.STRICT));

		this.mockMvc.perform(authorized(post("/api/admin/sources/5/disable"))).andExpect(status().isNoContent());
		assertThat(this.administration.calls).containsExactly("enableSource 5", "enableSource 5", "disableSource 5");
	}

	@Test
	void unknownOrImpossibleSourcesAreNotFound() throws Exception {
		this.mockMvc.perform(authorized(post("/api/admin/sources/-1/enable")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Not Found","status":404,"detail":"없는 소스: -1",
					 "instance":"/api/admin/sources/-1/enable"}
					""", JsonCompareMode.STRICT));
		assertThat(this.administration.calls).isEmpty();

		this.administration.failure = new UnknownSourceException(new SourceId(99));
		this.mockMvc.perform(authorized(post("/api/admin/sources/99/disable")))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void sourcePollIntervalBelowTheMinimumIsABadRequest() throws Exception {
		this.administration.intervalAccepted = false;

		this.mockMvc.perform(json(put("/api/admin/sources/5/poll-interval"), "{\"minutes\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Bad Request","status":400,"detail":"수집 주기는 15분 이상이다",
					 "instance":"/api/admin/sources/5/poll-interval"}
					""", JsonCompareMode.STRICT));
		assertThat(this.administration.calls).containsExactly("changeSourcePollInterval 5 0");
	}

}
