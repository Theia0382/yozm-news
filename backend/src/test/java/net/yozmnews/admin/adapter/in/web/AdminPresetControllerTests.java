package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import net.yozmnews.platform.application.WebSliceSecurity;
import net.yozmnews.source.PresetView;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.SourceRegistrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 프리셋 — 목록과 등록. 카탈로그에 없는 프리셋은 404 다.
 * <p>
 * 지키는 규칙: admin-R1 · admin-R2 · source-R1 · source-R2
 */
@WebMvcTest(controllers = AdminPresetController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeSourceAdministration.Config.class })
class AdminPresetControllerTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeSourceAdministration administration;

	@BeforeEach
	void reset() {
		this.administration.reset();
	}

	@Test
	void listsEachPresetWithItsSource() throws Exception {
		this.administration.presets = List.of(
				new PresetView("multi", "멀티일보", URI.create("https://multi.example"), LocalDate.parse("2026-10-20"), 2,
						new SourceId(3)),
				new PresetView("solo", "솔로뉴스", URI.create("https://solo.example"), LocalDate.parse("2026-10-21"), 1, null));

		this.mockMvc.perform(authorized(get("/api/admin/presets")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					[{"key":"multi","name":"멀티일보","homepage":"https://multi.example","termsReviewedAt":"2026-10-20",
					  "feedCount":2,"sourceId":3},
					 {"key":"solo","name":"솔로뉴스","homepage":"https://solo.example","termsReviewedAt":"2026-10-21",
					  "feedCount":1,"sourceId":null}]
					""", JsonCompareMode.STRICT));
	}

	@Test
	void registrationAnswersTheSourceOrThatItIsAlreadyRegistered() throws Exception {
		this.administration.registration = new SourceRegistrationResult.Registered(new SourceId(3));
		this.mockMvc.perform(authorized(post("/api/admin/presets/multi/registration")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"REGISTERED","sourceId":3,"problems":[]}
					""", JsonCompareMode.STRICT));

		this.administration.registration = new SourceRegistrationResult.PresetAlreadyRegistered("multi");
		this.mockMvc.perform(authorized(post("/api/admin/presets/multi/registration")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"ALREADY_REGISTERED","sourceId":null,"problems":[]}
					""", JsonCompareMode.STRICT));

		assertThat(this.administration.calls).containsExactly("registerPreset multi", "registerPreset multi");
	}

	@Test
	void presetMissingFromTheCatalogIsNotFound() throws Exception {
		this.administration.registration = new SourceRegistrationResult.PresetNotFound("gone");

		this.mockMvc.perform(authorized(post("/api/admin/presets/gone/registration")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Not Found","status":404,"detail":"카탈로그에 없는 프리셋이다: gone",
					 "instance":"/api/admin/presets/gone/registration"}
					""", JsonCompareMode.STRICT));
	}

}
