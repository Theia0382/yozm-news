package net.yozmnews.user.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.SignedInUsers;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 관심 설정 처리기 — 응답 모양, 판정 결과(저장 · 체계 버전이 바뀜 · 받지 않는 시간대 · 관심사가 김 · 키 없는 공급자 · 허용 목록에 없는 모델), 형식
 * 400, 키 없는 사용자도 쓴다(user-R5). 로그인 시험 도우미가 만든 계정은 설정 행이 없어 첫 요청이 기본값을 만든다. 실제 PostgreSQL 과 기본 체계 ·
 * 기본 허용 모델 목록으로 본다.
 * <p>
 * 지키는 규칙: user-R5 · user-R46 · user-R49 ~ user-R51
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InterestControllerTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	JdbcClient jdbc;

	private SignedInUsers.SignedInUser user;

	@BeforeEach
	void signIn() throws Exception {
		reset();
		this.user = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing)
			.signIn("interest@example.com");
	}

	@AfterEach
	void reset() {
		this.jdbc.sql("delete from users").update();
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void aUserWithoutAKeyReadsTheActiveTagSetAndTheDefaultPreferences() throws Exception {
		this.mockMvc.perform(get("/api/interests/tags").with(this.user))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tagSet.id").value(this.tagSets.activeTagSet().id().value()))
			.andExpect(jsonPath("$.tagSet.number").value(1))
			.andExpect(jsonPath("$.tagSet.topics[1].key").value("economy"))
			.andExpect(jsonPath("$.tagSet.topics[1].subtopics[2].key").value("economy.markets"))
			.andExpect(jsonPath("$.tagSet.regions[3].key").value("china"))
			.andExpect(jsonPath("$.tagSet.formats[4].key").value("press-release"))
			.andExpect(jsonPath("$.tagSet.formats[4].excludedByDefault").value(true))
			.andExpect(jsonPath("$.tagSet.formats[0].excludedByDefault").value(false))
			.andExpect(jsonPath("$.preferences.topics").isEmpty())
			.andExpect(jsonPath("$.preferences.excludedRegions").isEmpty())
			.andExpect(jsonPath("$.preferences.excludedFormats[0]").value("brief"))
			.andExpect(jsonPath("$.preferences.excludedFormats[1]").value("press-release"))
			.andExpect(jsonPath("$.preferences.excludedFormats.length()").value(2));
	}

	@Test
	void savingTagsNormalizesOrSaysTheTagSetChangedOrRejectsAnUnknownKey() throws Exception {
		long tagSet = this.tagSets.activeTagSet().id().value();

		this.mockMvc.perform(json(put("/api/interests/tags").with(this.user), """
				{"tagSetId":%d,"topics":["economy.markets","economy"],"excludedRegions":["us"],
				 "excludedFormats":["brief"]}""".formatted(tagSet)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"SAVED","preferences":{"topics":["economy"],"excludedRegions":["us"],
					 "excludedFormats":["brief"]},"activeTagSetId":null}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(json(put("/api/interests/tags").with(this.user), """
				{"tagSetId":%d,"topics":[],"excludedRegions":[],"excludedFormats":[]}""".formatted(tagSet + 1000)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"TAG_SET_CHANGED","preferences":null,"activeTagSetId":%d}""".formatted(tagSet),
					JsonCompareMode.STRICT));
		this.mockMvc.perform(json(put("/api/interests/tags").with(this.user), """
				{"tagSetId":%d,"topics":["sports.nowhere"],"excludedRegions":[],"excludedFormats":[]}""".formatted(tagSet)))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(json(put("/api/interests/tags").with(this.user), """
				{"tagSetId":%d,"topics":[]}""".formatted(tagSet)))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/interests/tags").with(this.user))
			.andExpect(jsonPath("$.preferences.topics[0]").value("economy"));
	}

	@Test
	void settingsHaveTheDefaultsAndSaveOrSayWhyNot() throws Exception {
		this.mockMvc.perform(get("/api/interests/settings").with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"timezone":"Asia/Seoul","digestTime":"07:00","interestText":"","interestTextMax":500,
					 "weeklyReport":true,"weeklyReportDay":"MONDAY","monthlyReport":true}""", JsonCompareMode.STRICT));

		this.mockMvc.perform(json(put("/api/interests/settings").with(this.user), settings("America/New_York", "06:30",
				"  전력망과 배터리  ")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"SAVED","settings":{"timezone":"America/New_York","digestTime":"06:30",
					 "interestText":"전력망과 배터리","interestTextMax":500,"weeklyReport":false,"weeklyReportDay":"FRIDAY",
					 "monthlyReport":false},"interestTextMax":null}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(json(put("/api/interests/settings").with(this.user), settings("+09:00", "06:30", "")))
			.andExpect(content().json("""
					{"result":"UNKNOWN_TIMEZONE","settings":null,"interestTextMax":null}""", JsonCompareMode.STRICT));
		this.mockMvc
			.perform(json(put("/api/interests/settings").with(this.user), settings("Asia/Seoul", "06:30", "가".repeat(501))))
			.andExpect(content().json("""
					{"result":"INTEREST_TEXT_TOO_LONG","settings":null,"interestTextMax":500}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(json(put("/api/interests/settings").with(this.user), settings("Asia/Seoul", "7:00", "")))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(json(put("/api/interests/settings").with(this.user), settings("Asia/Seoul", "24:00", "")))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(json(put("/api/interests/settings").with(this.user), """
				{"timezone":"Asia/Seoul","digestTime":"07:00","interestText":"","weeklyReport":true,
				 "weeklyReportDay":"FUNDAY","monthlyReport":true}"""))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/interests/settings").with(this.user))
			.andExpect(jsonPath("$.timezone").value("America/New_York"))
			.andExpect(jsonPath("$.interestText").value("전력망과 배터리"));
	}

	@Test
	void aUserWithoutAKeyHasNothingToChooseAndCannotChoose() throws Exception {
		this.mockMvc.perform(get("/api/interests/model").with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"choosable":[],"chosen":null,"effective":null,"reason":"NOT_CHOSEN"}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(json(put("/api/interests/model").with(this.user), """
				{"provider":"OPENAI","modelId":"gpt-6-astra"}"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"PROVIDER_WITHOUT_KEY","model":null}""", JsonCompareMode.STRICT));
	}

	@Test
	void aUserWithAKeyChoosesAnAllowedModelOfThatProvider() throws Exception {
		LlmKeyRows.give(this.jdbc, this.user.id().value());

		this.mockMvc.perform(get("/api/interests/model").with(this.user))
			.andExpect(content().json("""
					{"choosable":[{"provider":"OPENAI","id":"gpt-6.1-terra"},{"provider":"OPENAI","id":"gpt-6.1-sol"},
					  {"provider":"OPENAI","id":"gpt-6-luna"},{"provider":"OPENAI","id":"gpt-6-astra"}],
					 "chosen":null,"effective":{"provider":"OPENAI","id":"gpt-6.1-terra"},"reason":"NOT_CHOSEN"}""",
					JsonCompareMode.STRICT));
		this.mockMvc.perform(json(put("/api/interests/model").with(this.user), """
				{"provider":"OPENAI","modelId":"gpt-unknown"}"""))
			.andExpect(content().json("""
					{"result":"MODEL_NOT_ALLOWED","model":null}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(json(put("/api/interests/model").with(this.user), """
				{"provider":"OPENAI","modelId":"gpt-6.1-sol"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("SAVED"))
			.andExpect(jsonPath("$.model.chosen.id").value("gpt-6.1-sol"))
			.andExpect(jsonPath("$.model.effective.id").value("gpt-6.1-sol"))
			.andExpect(jsonPath("$.model.reason").value("CHOSEN"));
		this.mockMvc.perform(json(put("/api/interests/model").with(this.user), """
				{"provider":"GEMINI","modelId":"x"}"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void sourcesAreReadAndSavedWithoutAKey() throws Exception {
		this.mockMvc.perform(get("/api/interests/sources").with(this.user))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sources").isArray())
			.andExpect(jsonPath("$.excluded").isEmpty());
		this.mockMvc.perform(json(put("/api/interests/sources").with(this.user), """
				{"excluded":[%d]}""".formatted(Long.MAX_VALUE)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"SAVED","excluded":[]}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(json(put("/api/interests/sources").with(this.user), """
				{"excluded":[0]}"""))
			.andExpect(status().isBadRequest());
	}

	private static String settings(String timezone, String time, String interestText) {
		return """
				{"timezone":"%s","digestTime":"%s","interestText":"%s","weeklyReport":false,"weeklyReportDay":"FRIDAY",
				 "monthlyReport":false}""".formatted(timezone, time, interestText);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
