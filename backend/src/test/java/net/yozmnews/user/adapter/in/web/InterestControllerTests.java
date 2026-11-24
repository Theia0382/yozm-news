package net.yozmnews.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.SignedInUsers;
import net.yozmnews.account.application.PasswordHashing;
import net.yozmnews.account.domain.account.AccountRepository;
import net.yozmnews.account.domain.credential.PasswordCredentialRepository;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.UserTables;
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
 * 관심 설정 처리기 — 응답 모양, 판정 결과(저장 · 체계 버전이 바뀜 · 받지 않는 시간대 · 키 없는 공급자 · 허용 목록에 없는 모델 ·
 * 구독 제안 거절), 형식 400, 키 없는 사용자도 쓴다(account-R5). 로그인 시험 도우미가 만든 계정은 설정 행이 없어 첫 요청이 기본값을
 * 만든다. 실제 PostgreSQL 과 기본 체계 · 기본 허용 모델 목록으로 본다.
 * <p>
 * 지키는 규칙: account-R5 · user-R46 · user-R49 ~ user-R51 · user-R64
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
		UserTables.deleteAll(this.jdbc);
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
					{"timezone":"Asia/Seoul","digestTime":"07:00","weeklyReport":true,"weeklyReportDay":"MONDAY",
					 "monthlyReport":true}""", JsonCompareMode.STRICT));

		this.mockMvc.perform(json(put("/api/interests/settings").with(this.user), settings("America/New_York", "06:30")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"SAVED","settings":{"timezone":"America/New_York","digestTime":"06:30","weeklyReport":false,
					 "weeklyReportDay":"FRIDAY","monthlyReport":false}}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(json(put("/api/interests/settings").with(this.user), settings("+09:00", "06:30")))
			.andExpect(content().json("""
					{"result":"UNKNOWN_TIMEZONE","settings":null}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(json(put("/api/interests/settings").with(this.user), settings("Asia/Seoul", "7:00")))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(json(put("/api/interests/settings").with(this.user), settings("Asia/Seoul", "24:00")))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(json(put("/api/interests/settings").with(this.user), """
				{"timezone":"Asia/Seoul","digestTime":"07:00","weeklyReport":true,"weeklyReportDay":"FUNDAY",
				 "monthlyReport":true}"""))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/interests/settings").with(this.user))
			.andExpect(content().json("""
					{"timezone":"America/New_York","digestTime":"06:30","weeklyReport":false,"weeklyReportDay":"FRIDAY",
					 "monthlyReport":false}""", JsonCompareMode.STRICT));
	}

	@Test
	void aBodyThatStillCarriesTheRemovedInterestTextIsSavedWithoutIt() throws Exception {
		// 업그레이드 전에 받은 화면은 관심사 칸을 실어 보낸다 — 모르는 칸이라 읽지 않고 나머지를 저장한다
		this.mockMvc.perform(json(put("/api/interests/settings").with(this.user), """
				{"timezone":"Europe/Paris","digestTime":"06:15","interestText":"반도체","weeklyReport":false,
				 "weeklyReportDay":"FRIDAY","monthlyReport":false}"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"SAVED","settings":{"timezone":"Europe/Paris","digestTime":"06:15","weeklyReport":false,
					 "weeklyReportDay":"FRIDAY","monthlyReport":false}}""", JsonCompareMode.STRICT));
	}

	@Test
	void volumeHasTheDefaultsAndSavesOrSaysWhichFieldIsOutOfRange() throws Exception {
		String presets = """
				"presets":[{"volume":"FEW","count":20},{"volume":"NORMAL","count":40},{"volume":"MANY","count":60}],
				"countRange":{"min":10,"max":100},"bigSlotRange":{"min":0,"max":10},
				"summaryLengths":["SHORT","NORMAL","DETAILED"]""";
		this.mockMvc.perform(get("/api/interests/volume").with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"volume":"NORMAL","customCount":null,"bigSlots":3,"summaryLength":"NORMAL",%s}"""
				.formatted(presets), JsonCompareMode.STRICT));

		this.mockMvc.perform(json(put("/api/interests/volume").with(this.user), """
				{"volume":"CUSTOM","customCount":100,"bigSlots":0,"summaryLength":"DETAILED"}"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"SAVED","volume":{"volume":"CUSTOM","customCount":100,"bigSlots":0,
					 "summaryLength":"DETAILED",%s},"range":null}""".formatted(presets), JsonCompareMode.STRICT));
		this.mockMvc.perform(json(put("/api/interests/volume").with(this.user), """
				{"volume":"CUSTOM","customCount":101,"bigSlots":0,"summaryLength":"SHORT"}"""))
			.andExpect(content().json("""
					{"result":"COUNT_OUT_OF_RANGE","volume":null,"range":{"min":10,"max":100}}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(json(put("/api/interests/volume").with(this.user), """
				{"volume":"CUSTOM","customCount":9,"bigSlots":11,"summaryLength":"SHORT"}"""))
			.andExpect(content().json("""
					{"result":"COUNT_OUT_OF_RANGE","volume":null,"range":{"min":10,"max":100}}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(json(put("/api/interests/volume").with(this.user), """
				{"volume":"MANY","bigSlots":11,"summaryLength":"SHORT"}"""))
			.andExpect(content().json("""
					{"result":"BIG_SLOTS_OUT_OF_RANGE","volume":null,"range":{"min":0,"max":10}}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(json(put("/api/interests/volume").with(this.user), """
				{"volume":"MANY","customCount":45,"bigSlots":3,"summaryLength":"SHORT"}"""))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(json(put("/api/interests/volume").with(this.user), """
				{"volume":"MANY","customCount":200,"bigSlots":3,"summaryLength":"SHORT"}"""))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(json(put("/api/interests/volume").with(this.user), """
				{"volume":"CUSTOM","bigSlots":3,"summaryLength":"SHORT"}"""))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(json(put("/api/interests/volume").with(this.user), """
				{"volume":"LOTS","bigSlots":3,"summaryLength":"SHORT"}"""))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(json(put("/api/interests/volume").with(this.user), """
				{"volume":"MANY","summaryLength":"SHORT"}"""))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(json(put("/api/interests/volume").with(this.user), """
				{"volume":"MANY","bigSlots":3}"""))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(json(put("/api/interests/volume").with(this.user), """
				{"volume":"MANY","bigSlots":3,"summaryLength":"LONG"}"""))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/interests/volume").with(this.user))
			.andExpect(jsonPath("$.volume").value("CUSTOM"))
			.andExpect(jsonPath("$.customCount").value(100))
			.andExpect(jsonPath("$.bigSlots").value(0))
			.andExpect(jsonPath("$.summaryLength").value("DETAILED"));

		this.mockMvc.perform(json(put("/api/interests/volume").with(this.user), """
				{"volume":"FEW","bigSlots":10,"summaryLength":"SHORT"}"""))
			.andExpect(jsonPath("$.result").value("SAVED"));
		this.mockMvc.perform(get("/api/interests/volume").with(this.user))
			.andExpect(jsonPath("$.volume").value("FEW"))
			.andExpect(jsonPath("$.customCount").value(nullValue()))
			.andExpect(jsonPath("$.bigSlots").value(10))
			.andExpect(jsonPath("$.summaryLength").value("SHORT"));
	}

	@Test
	void theEffortIsMediumByDefaultAndSavesWithoutAKeyOrAChosenModel() throws Exception {
		String efforts = """
				"efforts":["LOW","MEDIUM","HIGH"]""";
		this.mockMvc.perform(get("/api/interests/model").with(this.user))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.effort").value("MEDIUM"));
		this.mockMvc.perform(json(put("/api/interests/effort").with(this.user), """
				{"effort":"LOW"}"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"choosable":[],"chosen":null,"effective":null,"reason":"NOT_CHOSEN","effort":"LOW",%s}"""
				.formatted(efforts), JsonCompareMode.STRICT));
		this.mockMvc.perform(json(put("/api/interests/effort").with(this.user), """
				{"effort":"NONE"}"""))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(json(put("/api/interests/effort").with(this.user), "{}"))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/interests/model").with(this.user))
			.andExpect(jsonPath("$.effort").value("LOW"));

		this.mockMvc.perform(json(put("/api/interests/effort").with(this.user), """
				{"effort":"HIGH"}"""))
			.andExpect(jsonPath("$.effort").value("HIGH"));
		this.mockMvc.perform(get("/api/interests/model").with(this.user))
			.andExpect(jsonPath("$.effort").value("HIGH"));
	}

	@Test
	void aUserWithoutAKeyHasNothingToChooseAndCannotChoose() throws Exception {
		this.mockMvc.perform(get("/api/interests/model").with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"choosable":[],"chosen":null,"effective":null,"reason":"NOT_CHOSEN","effort":"MEDIUM",
					 "efforts":["LOW","MEDIUM","HIGH"]}""", JsonCompareMode.STRICT));
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
					 "chosen":null,"effective":{"provider":"OPENAI","id":"gpt-6.1-terra"},"reason":"NOT_CHOSEN",
					 "effort":"MEDIUM","efforts":["LOW","MEDIUM","HIGH"]}""",
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

	@Test
	void decliningASuggestionWritesItOrSaysTheTagSetChangedOrRejectsANonSubtopic() throws Exception {
		long tagSet = this.tagSets.activeTagSet().id().value();

		this.mockMvc.perform(json(post("/api/interests/declined-suggestions").with(this.user), """
				{"tagSetId":%d,"topic":"economy.markets"}""".formatted(tagSet)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"DECLINED","topic":"economy.markets","activeTagSetId":null}""",
					JsonCompareMode.STRICT));
		assertThat(this.jdbc.sql("""
				select tag_set_id, topic_key, interest_level from user_topic_suggestion_declines
				""").query((rs, n) -> rs.getLong(1) + " " + rs.getString(2) + " " + rs.getDouble(3)).list())
			.as("신호가 없는 사용자의 거절 때 관심은 0")
			.containsExactly(tagSet + " economy.markets 0.0");
		this.mockMvc.perform(json(post("/api/interests/declined-suggestions").with(this.user), """
				{"tagSetId":%d,"topic":"economy.markets"}""".formatted(tagSet + 1000)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"TAG_SET_CHANGED","topic":null,"activeTagSetId":%d}""".formatted(tagSet),
					JsonCompareMode.STRICT));
		for (String body : new String[] { "{\"tagSetId\":%d,\"topic\":\"economy\"}".formatted(tagSet),
				"{\"tagSetId\":%d,\"topic\":\"sports.nowhere\"}".formatted(tagSet),
				"{\"tagSetId\":0,\"topic\":\"economy.markets\"}", "{\"tagSetId\":%d,\"topic\":\" \"}".formatted(tagSet),
				"{\"tagSetId\":%d}".formatted(tagSet) }) {
			this.mockMvc.perform(json(post("/api/interests/declined-suggestions").with(this.user), body))
				.andExpect(status().isBadRequest());
		}
	}

	private static String settings(String timezone, String time) {
		return """
				{"timezone":"%s","digestTime":"%s","weeklyReport":false,"weeklyReportDay":"FRIDAY",
				 "monthlyReport":false}""".formatted(timezone, time);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
