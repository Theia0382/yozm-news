package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static net.yozmnews.admin.adapter.in.web.AdminRequests.json;
import static net.yozmnews.admin.adapter.in.web.AdminRequests.withCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import net.yozmnews.account.application.WebSliceSecurity;
import net.yozmnews.admin.adapter.in.web.FakeMeasurementClockAdministration.Advance;
import net.yozmnews.platform.AdvanceRefusal;
import net.yozmnews.platform.ClockAdvanceResult;
import net.yozmnews.platform.ClockJumpView;
import net.yozmnews.platform.MeasurementClockStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 측정용 시계 — 상태 응답 모양, 넘기기 인자 전달(ISO-8601 기간 · 시각), 거부 까닭마다의 400, 측정용 시계가 아닐 때 409, 토큰
 * 없음 401.
 */
@WebMvcTest(controllers = AdminMeasurementClockController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeMeasurementClockAdministration.Config.class })
class AdminMeasurementClockControllerTests {

	private static final Instant STARTED = Instant.parse("2026-11-09T22:05:00Z");

	private static final Instant LATER = Instant.parse("2026-11-10T22:05:00Z");

	private static final MeasurementClockStatus.On ON = new MeasurementClockStatus.On(LATER, STARTED,
			List.of(new ClockJumpView(STARTED, LATER)));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeMeasurementClockAdministration administration;

	@BeforeEach
	void reset() {
		this.administration.reset();
	}

	@Test
	void statusCarriesNowStartAndJumps() throws Exception {
		this.administration.status = ON;

		this.mockMvc.perform(authorized(get("/api/admin/measurement-clock")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"now":"2026-11-10T22:05:00Z","startedAt":"2026-11-09T22:05:00Z",
					 "jumps":[{"from":"2026-11-09T22:05:00Z","to":"2026-11-10T22:05:00Z"}]}""", JsonCompareMode.STRICT));
	}

	@Test
	void advancePassesTheLengthOrTheArrival() throws Exception {
		this.administration.advance = new ClockAdvanceResult.Advanced(ON);

		this.mockMvc.perform(json(post("/api/admin/measurement-clock/advance"), "{\"by\":\"PT24H\"}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"now":"2026-11-10T22:05:00Z","startedAt":"2026-11-09T22:05:00Z",
					 "jumps":[{"from":"2026-11-09T22:05:00Z","to":"2026-11-10T22:05:00Z"}]}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(json(post("/api/admin/measurement-clock/advance"), "{\"to\":\"2026-11-10T22:05:00Z\"}"))
			.andExpect(status().isOk());

		assertThat(this.administration.received).containsExactly(new Advance(Duration.ofHours(24), null),
				new Advance(null, LATER));
	}

	@Test
	void eachRefusalIsBadRequestWithItsDetail() throws Exception {
		List<List<String>> cases = List.of(
				List.of("BOTH_GIVEN", "넘길 길이(by)와 도착 시각(to) 가운데 하나만 준다"),
				List.of("NONE_GIVEN", "넘길 길이(by)나 도착 시각(to) 가운데 하나를 준다"),
				List.of("NOT_POSITIVE", "넘길 길이는 0 보다 길어야 한다"),
				List.of("NOT_AHEAD", "도착 시각은 지금보다 뒤여야 한다 — 측정용 시계는 앞으로만 넘긴다"));
		for (List<String> refusal : cases) {
			this.administration.advance = new ClockAdvanceResult.Rejected(AdvanceRefusal.valueOf(refusal.get(0)));
			this.mockMvc.perform(json(post("/api/admin/measurement-clock/advance"), "{}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("""
						{"type":"about:blank","title":"Bad Request","status":400,"detail":"%s",
						 "instance":"/api/admin/measurement-clock/advance"}""".formatted(refusal.get(1)),
						JsonCompareMode.STRICT));
		}
	}

	@Test
	void withoutTheMeasurementClockBothOperationsAreConflict() throws Exception {
		this.mockMvc.perform(authorized(get("/api/admin/measurement-clock")))
			.andExpect(status().isConflict())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Conflict","status":409,
					 "detail":"측정용 시계가 켜져 있지 않다 — 이 인스턴스의 시계는 실제 시간이다",
					 "instance":"/api/admin/measurement-clock"}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(json(post("/api/admin/measurement-clock/advance"), "{\"by\":\"PT1H\"}"))
			.andExpect(status().isConflict());
	}

	@Test
	void malformedBodiesAreBadRequestAndCallNothing() throws Exception {
		for (String body : List.of("{\"by\":\"하루\"}", "{\"to\":\"어제\"}", "[]")) {
			this.mockMvc.perform(json(post("/api/admin/measurement-clock/advance"), body))
				.andExpect(status().isBadRequest());
		}
		assertThat(this.administration.received).isEmpty();
	}

	@Test
	void requestWithoutTheTokenIsUnauthorizedAndCallsNothing() throws Exception {
		this.mockMvc.perform(get("/api/admin/measurement-clock")).andExpect(status().isUnauthorized());
		this.mockMvc
			.perform(withCsrf(post("/api/admin/measurement-clock/advance").contentType("application/json")
				.content("{\"by\":\"PT1H\"}")))
			.andExpect(status().isUnauthorized());
		assertThat(this.administration.received).isEmpty();
	}

}
