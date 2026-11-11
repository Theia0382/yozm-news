package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static net.yozmnews.admin.adapter.in.web.AdminRequests.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import net.yozmnews.platform.ChangeActor;
import net.yozmnews.platform.application.WebSliceSecurity;
import net.yozmnews.tag.CountBucket;
import net.yozmnews.tag.EvaluationOverview;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.ProbabilityHistogram;
import net.yozmnews.tag.Share;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagThresholds;
import net.yozmnews.tag.ThresholdChangeResult;
import net.yozmnews.tag.ThresholdChangeView;
import net.yozmnews.tag.ThresholdCondition;
import net.yozmnews.tag.ThresholdKindStatus;
import net.yozmnews.tag.ThresholdPreviewResult;
import net.yozmnews.tag.ThresholdStatusView;
import net.yozmnews.tag.ThresholdWarning;
import net.yozmnews.tag.TokenSummary;
import net.yozmnews.tag.TopicStatusCounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 태그 기준 · 저장 기준 — 상태 · 미리보기 · 변경의 JSON 모양과 판별자, 운영자 행위자, 범위 밖 값 · 본문 · 경로의 400, 토큰.
 * <p>
 * 지키는 규칙: admin-R1 · admin-R3 · tag-R11 · tag-R35 · tag-R36
 */
@WebMvcTest(controllers = AdminThresholdController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeTagSetAdministration.Config.class })
class AdminThresholdControllerTests {

	private static final Instant FROM = Instant.parse("2026-10-27T15:00:00Z");

	private static final Instant TO = Instant.parse("2026-11-03T15:00:00Z");

	private static final String PREVIEW = """
			{"kind":"TOPIC","tagThreshold":0.4,"storeThreshold":0.1,"from":"2026-10-28","to":"2026-11-03",
			 "zone":"Asia/Seoul"}
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
	void theStatusCarriesTheVersionAsANumberTheConditionsAndTheHistory() throws Exception {
		this.administration.status = new ThresholdStatusView(new TagSetId(3), 2,
				new ThresholdKindStatus(thresholds(MultiTagKind.TOPIC, "0.150", "0.100"), new BigDecimal("0.200"),
						Set.of(ThresholdCondition.TAG_BELOW_PAST_STORE)),
				new ThresholdKindStatus(thresholds(MultiTagKind.REGION, "0.500", "0.100"), new BigDecimal("0.100"),
						Set.of()),
				List.of(new ThresholdChangeView(MultiTagKind.TOPIC, new BigDecimal("0.600"), new BigDecimal("0.200"),
						new BigDecimal("0.150"), new BigDecimal("0.100"), ChangeActor.OPERATOR,
						Instant.parse("2026-11-04T03:00:00Z"))));

		this.mockMvc.perform(authorized(get("/api/admin/thresholds")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"version":3,"versionNumber":2,
					 "topic":{"current":{"kind":"TOPIC","tagThreshold":0.150,"storeThreshold":0.100},
					          "pastMaxStoreThreshold":0.200,"conditions":["TAG_BELOW_PAST_STORE"]},
					 "region":{"current":{"kind":"REGION","tagThreshold":0.500,"storeThreshold":0.100},
					           "pastMaxStoreThreshold":0.100,"conditions":[]},
					 "changes":[{"kind":"TOPIC","previousTagThreshold":0.600,"previousStoreThreshold":0.200,
					             "tagThreshold":0.150,"storeThreshold":0.100,"actor":"OPERATOR",
					             "changedAt":"2026-11-04T03:00:00Z"}]}
					"""));
	}

	@Test
	void aPreviewCarriesBothOverviewsAndTheChangedCountWithItsDiscriminator() throws Exception {
		this.administration.preview = new ThresholdPreviewResult.Previewed(new TagSetId(3),
				thresholds(MultiTagKind.TOPIC, "0.500", "0.100"), thresholds(MultiTagKind.TOPIC, "0.400", "0.100"),
				Set.of(), 120, 7, overview(120, 100), overview(120, 110));

		this.mockMvc.perform(json(post("/api/admin/threshold-previews"), PREVIEW))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"PREVIEWED","version":3,
					 "current":{"kind":"TOPIC","tagThreshold":0.500,"storeThreshold":0.100},
					 "proposed":{"kind":"TOPIC","tagThreshold":0.400,"storeThreshold":0.100},
					 "warnings":[],"evaluations":120,"changed":7,
					 "before":{"version":3,"evaluations":120,"topicStatus":{"tagged":{"count":100}}},
					 "after":{"version":3,"evaluations":120,"topicStatus":{"tagged":{"count":110}}}}
					"""));
		assertThat(this.administration.calls).containsExactly("previewThresholds TOPIC 0.4 0.1 " + FROM + " " + TO);
	}

	@Test
	void theSameValuesPreviewAsUnchangedAndOutOfRangeValuesAreABadRequest() throws Exception {
		this.administration.preview = new ThresholdPreviewResult.Unchanged(new TagSetId(3),
				thresholds(MultiTagKind.TOPIC, "0.500", "0.100"));
		this.mockMvc.perform(json(post("/api/admin/threshold-previews"), PREVIEW))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"UNCHANGED","version":3,
					 "current":{"kind":"TOPIC","tagThreshold":0.500,"storeThreshold":0.100},
					 "proposed":null,"warnings":[],"evaluations":null,"changed":null,"before":null,"after":null}
					"""));

		this.administration.preview = new ThresholdPreviewResult.OutOfRange();
		this.mockMvc.perform(json(post("/api/admin/threshold-previews"), PREVIEW))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"status":400,"detail":"태그 기준 · 저장 기준은 0 이상 1 이하, 소수 셋째 자리까지다"}
					"""));
	}

	@Test
	void aPreviewWithABadPeriodOrBodyIsABadRequestWithoutCallingThePublicApi() throws Exception {
		this.mockMvc.perform(json(post("/api/admin/threshold-previews"), PREVIEW.replace("Asia/Seoul", "Mars/Base")))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(json(post("/api/admin/threshold-previews"), """
				{"kind":"TOPIC","tagThreshold":0.4,"from":"2026-10-28","to":"2026-11-03","zone":"Asia/Seoul"}
				""")).andExpect(status().isBadRequest());
		this.mockMvc.perform(json(post("/api/admin/threshold-previews"), PREVIEW.replace("TOPIC", "FORMAT")))
			.andExpect(status().isBadRequest());
		assertThat(this.administration.calls).isEmpty();
	}

	@Test
	void aChangeIsMadeAsTheOperatorAndCarriesTheVersionPreviousAndWarnings() throws Exception {
		this.administration.change = new ThresholdChangeResult.Changed(new TagSetId(4),
				thresholds(MultiTagKind.REGION, "0.500", "0.100"), thresholds(MultiTagKind.REGION, "0.050", "0.100"),
				Set.of(ThresholdWarning.TAG_BELOW_STORE));

		this.mockMvc.perform(json(put("/api/admin/thresholds/REGION"), """
				{"tagThreshold":0.05,"storeThreshold":0.1}
				"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"CHANGED","version":4,
					 "previous":{"kind":"REGION","tagThreshold":0.500,"storeThreshold":0.100},
					 "current":{"kind":"REGION","tagThreshold":0.050,"storeThreshold":0.100},
					 "warnings":["TAG_BELOW_STORE"]}
					"""));
		// 운영자 요청은 OPERATOR 로 부른다 — 이슈 코멘트 인계
		assertThat(this.administration.calls).containsExactly("changeThresholds REGION 0.05 0.1 OPERATOR");
	}

	@Test
	void theSameValuesChangeNothingAndOutOfRangeValuesAreABadRequest() throws Exception {
		this.administration.change = new ThresholdChangeResult.Unchanged(new TagSetId(4),
				thresholds(MultiTagKind.TOPIC, "0.500", "0.100"));
		this.mockMvc.perform(json(put("/api/admin/thresholds/TOPIC"), """
				{"tagThreshold":0.5,"storeThreshold":0.1}
				"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"UNCHANGED","version":4,"previous":null,
					 "current":{"kind":"TOPIC","tagThreshold":0.500,"storeThreshold":0.100},"warnings":[]}
					"""));

		this.administration.change = new ThresholdChangeResult.OutOfRange();
		this.mockMvc.perform(json(put("/api/admin/thresholds/TOPIC"), """
				{"tagThreshold":1.5,"storeThreshold":0.1}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"detail":"태그 기준 · 저장 기준은 0 이상 1 이하, 소수 셋째 자리까지다"}
					"""));
	}

	@Test
	void aChangeWithAnUnknownKindOrMissingValueIsABadRequest() throws Exception {
		this.mockMvc.perform(json(put("/api/admin/thresholds/FORMAT"), """
				{"tagThreshold":0.5,"storeThreshold":0.1}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		this.mockMvc.perform(json(put("/api/admin/thresholds/TOPIC"), """
				{"tagThreshold":0.5}
				"""))
			.andExpect(status().isBadRequest());
		assertThat(this.administration.calls).isEmpty();
	}

	@Test
	void theThresholdsNeedTheOperatorToken() throws Exception {
		this.mockMvc.perform(get("/api/admin/thresholds")).andExpect(status().isUnauthorized());
		this.mockMvc
			.perform(post("/api/admin/threshold-previews").contentType(MediaType.APPLICATION_JSON).content(PREVIEW))
			.andExpect(status().isUnauthorized());
		this.mockMvc.perform(put("/api/admin/thresholds/TOPIC").contentType(MediaType.APPLICATION_JSON)
			.content("{\"tagThreshold\":0.5,\"storeThreshold\":0.1}")).andExpect(status().isUnauthorized());
	}

	private static TagThresholds thresholds(MultiTagKind kind, String tag, String store) {
		return new TagThresholds(kind, new BigDecimal(tag), new BigDecimal(store));
	}

	/** 미리보기 응답 모양을 보는 최소 평가 현황 — 붙은 수만 다르다 */
	private static EvaluationOverview overview(long evaluations, long tagged) {
		Share none = new Share(0, 0.0);
		return new EvaluationOverview(new TagSetId(3), FROM, TO, evaluations,
				new TopicStatusCounts(new Share(tagged, (double) tagged / evaluations), none, none), List.of(),
				List.of(), List.of(), List.of(), List.of(new CountBucket(0, false, none)), List.of(),
				new ProbabilityHistogram(new BigDecimal("0.1"), false, List.of()),
				new ProbabilityHistogram(new BigDecimal("0.1"), false, List.of()),
				new ProbabilityHistogram(null, false, List.of()), List.of(), 0, new TokenSummary(null, null, null,
						List.of()));
	}

}
