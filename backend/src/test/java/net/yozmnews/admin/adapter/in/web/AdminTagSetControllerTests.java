package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static net.yozmnews.admin.adapter.in.web.AdminRequests.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.LineageKind;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagKind;
import net.yozmnews.evaluation.TagLineageView;
import net.yozmnews.evaluation.TagRef;
import net.yozmnews.evaluation.TagSetActivationResult;
import net.yozmnews.evaluation.TagSetChanges;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetProblemView;
import net.yozmnews.evaluation.TagSetReviewResult;
import net.yozmnews.evaluation.TagSetVersionView;
import net.yozmnews.evaluation.TagThresholds;
import net.yozmnews.evaluation.ThresholdDifference;
import net.yozmnews.platform.application.WebSliceSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 태그 체계 — 버전 목록 · 원문, 원문의 확인 · 활성화의 판별자 · 속성 · 400 · 404.
 * <p>
 * 지키는 규칙: admin-R1 · admin-R2 · admin-R3 · evaluation-R16 · evaluation-R25
 */
@WebMvcTest(controllers = AdminTagSetController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeTagSetAdministration.Config.class })
class AdminTagSetControllerTests {

	private static final TagSetChanges CHANGES = new TagSetChanges(List.of(new TagRef(TagKind.REGION, "north-korea")),
			List.of(new TagRef(TagKind.FORMAT, "brief")), List.of(new TagRef(TagKind.TOPIC, "politics.executive")),
			List.of(new TagLineageView(TagKind.REGION, LineageKind.RENAME, List.of("japan"), List.of("nippon"))), true,
			false,
			List.of(new ThresholdDifference(new TagThresholds(MultiTagKind.TOPIC, new BigDecimal("0.500"), new BigDecimal("0.100")),
					new TagThresholds(MultiTagKind.TOPIC, new BigDecimal("0.600"), new BigDecimal("0.100")))));

	private static final String CHANGES_JSON = """
			{"added":[{"kind":"REGION","key":"north-korea"}],"removed":[{"kind":"FORMAT","key":"brief"}],
			 "revised":[{"kind":"TOPIC","key":"politics.executive"}],
			 "lineage":[{"kind":"REGION","change":"RENAME","from":["japan"],"to":["nippon"]}],
			 "questionsRevised":true,"importanceRevised":false,
			 "thresholds":[{"current":{"kind":"TOPIC","tagThreshold":0.500,"storeThreshold":0.100},
			                "proposed":{"kind":"TOPIC","tagThreshold":0.600,"storeThreshold":0.100}}]}
			""";

	private static final TagSetProblemView PROBLEM = new TagSetProblemView("lineage[0]", "LINEAGE_SOURCE_MISSING",
			"이전 키 'nowhere' 가 기준 버전의 region 에 없다", 230, 5);

	private static final String PROBLEM_JSON = """
			{"path":"lineage[0]","code":"LINEAGE_SOURCE_MISSING","message":"이전 키 'nowhere' 가 기준 버전의 region 에 없다",
			 "line":230,"column":5}
			""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeTagSetAdministration administration;

	@BeforeEach
	void reset() {
		this.administration.reset();
	}

	@Test
	void listsVersionsLatestFirstWithTheMaximumPeriod() throws Exception {
		this.administration.versions = List.of(
				new TagSetVersionView(new TagSetId(7), 2, true, ChangeActor.OPERATOR, Instant.parse("2026-11-02T03:00:00Z")),
				new TagSetVersionView(new TagSetId(1), 1, false, ChangeActor.SYSTEM, Instant.parse("2026-11-01T00:00:00Z")));

		this.mockMvc.perform(authorized(get("/api/admin/tag-sets")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"versions":[
					   {"id":7,"number":2,"active":true,"createdBy":"OPERATOR","createdAt":"2026-11-02T03:00:00Z"},
					   {"id":1,"number":1,"active":false,"createdBy":"SYSTEM","createdAt":"2026-11-01T00:00:00Z"}],
					 "maxReevaluationDays":30}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void servesTheTextOfAVersionAndNotFoundForAnUnknownOne() throws Exception {
		this.administration.text = Optional.of("questions:\n");
		this.mockMvc.perform(authorized(get("/api/admin/tag-sets/3/text")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"text":"questions:\\n"}
					""", JsonCompareMode.STRICT));

		this.administration.text = Optional.empty();
		this.mockMvc.perform(authorized(get("/api/admin/tag-sets/9/text")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Not Found","status":404,"detail":"없는 태그 체계 버전: 9",
					 "instance":"/api/admin/tag-sets/9/text"}
					""", JsonCompareMode.STRICT));

		this.mockMvc.perform(authorized(get("/api/admin/tag-sets/0/text")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Not Found","status":404,"detail":"없는 태그 체계 버전: 0",
					 "instance":"/api/admin/tag-sets/0/text"}
					""", JsonCompareMode.STRICT));

		assertThat(this.administration.calls).containsExactly("versionText 3", "versionText 9");
	}

	@Test
	void reviewAnswersEachOutcomeAndDefaultsThePeriodToZero() throws Exception {
		this.administration.review = new TagSetReviewResult.Ready(CHANGES, 2_400);
		this.mockMvc.perform(json(post("/api/admin/tag-set-reviews"), "{\"text\":\"원문\",\"recentDays\":7}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"READY","changes":%s,"recentArticles":2400,"problems":[]}
					""".formatted(CHANGES_JSON), JsonCompareMode.STRICT));

		this.administration.review = new TagSetReviewResult.Unchanged();
		this.mockMvc.perform(json(post("/api/admin/tag-set-reviews"), "{\"text\":\"같은 원문\"}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"UNCHANGED","changes":null,"recentArticles":null,"problems":[]}
					""", JsonCompareMode.STRICT));

		this.administration.review = new TagSetReviewResult.Invalid(List.of(PROBLEM));
		this.mockMvc.perform(json(post("/api/admin/tag-set-reviews"), "{\"text\":\"틀린 원문\",\"recentDays\":0}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"INVALID_TAG_SET","changes":null,"recentArticles":null,"problems":[%s]}
					""".formatted(PROBLEM_JSON), JsonCompareMode.STRICT));

		assertThat(this.administration.calls).containsExactly("review 7 원문", "review 0 같은 원문", "review 0 틀린 원문");
	}

	@Test
	void activationAnswersEachOutcomeAsTheOperator() throws Exception {
		this.administration.activation = new TagSetActivationResult.Activated(
				new TagSetVersionView(new TagSetId(12), 3, true, ChangeActor.OPERATOR, Instant.parse("2026-11-02T03:00:00Z")),
				CHANGES, 2_350);
		this.mockMvc.perform(json(post("/api/admin/tag-sets"), "{\"text\":\"원문\",\"recentDays\":7}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"ACTIVATED","id":12,"number":3,"changes":%s,"requeued":2350,"problems":[]}
					""".formatted(CHANGES_JSON), JsonCompareMode.STRICT));

		this.administration.activation = new TagSetActivationResult.Unchanged();
		this.mockMvc.perform(json(post("/api/admin/tag-sets"), "{\"text\":\"같은 원문\"}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"UNCHANGED","id":null,"number":null,"changes":null,"requeued":null,"problems":[]}
					""", JsonCompareMode.STRICT));

		this.administration.activation = new TagSetActivationResult.Invalid(List.of(PROBLEM));
		this.mockMvc.perform(json(post("/api/admin/tag-sets"), "{\"text\":\"틀린 원문\"}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"INVALID_TAG_SET","id":null,"number":null,"changes":null,"requeued":null,"problems":[%s]}
					""".formatted(PROBLEM_JSON), JsonCompareMode.STRICT));

		assertThat(this.administration.calls).containsExactly("activate 7 OPERATOR 원문", "activate 0 OPERATOR 같은 원문",
				"activate 0 OPERATOR 틀린 원문");
	}

	@Test
	void aPeriodOutOfRangeIsABadRequest() throws Exception {
		this.administration.review = new TagSetReviewResult.DaysOutOfRange();
		this.mockMvc.perform(json(post("/api/admin/tag-set-reviews"), "{\"text\":\"원문\",\"recentDays\":31}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Bad Request","status":400,"detail":"재평가 기간은 0일에서 30일까지다",
					 "instance":"/api/admin/tag-set-reviews"}
					""", JsonCompareMode.STRICT));

		this.administration.activation = new TagSetActivationResult.DaysOutOfRange();
		this.mockMvc.perform(json(post("/api/admin/tag-sets"), "{\"text\":\"원문\",\"recentDays\":-1}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Bad Request","status":400,"detail":"재평가 기간은 0일에서 30일까지다",
					 "instance":"/api/admin/tag-sets"}
					""", JsonCompareMode.STRICT));

		assertThat(this.administration.calls).containsExactly("review 31 원문", "activate -1 OPERATOR 원문");
	}

	@Test
	void aBodyWithoutTheTextIsABadRequest() throws Exception {
		this.mockMvc.perform(json(post("/api/admin/tag-sets"), "{\"recentDays\":7}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		this.mockMvc.perform(json(post("/api/admin/tag-set-reviews"), "{}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

		assertThat(this.administration.calls).isEmpty();
	}

	@Test
	void theOperatorTokenGuardsTheTagSetPaths() throws Exception {
		this.mockMvc.perform(get("/api/admin/tag-sets")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(post("/api/admin/tag-sets").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"원문\"}"))
			.andExpect(status().isUnauthorized());

		assertThat(this.administration.calls).isEmpty();
	}

}
