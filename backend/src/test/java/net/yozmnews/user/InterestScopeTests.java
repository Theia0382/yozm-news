package net.yozmnews.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.SignedInUsers;
import net.yozmnews.account.application.PasswordHashing;
import net.yozmnews.account.domain.account.AccountRepository;
import net.yozmnews.account.domain.credential.PasswordCredentialRepository;
import net.yozmnews.tag.TagSetDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 관심 설정의 사용자 격리 — 사용자 A 의 세션으로 태그 선호 · 제외 소스 · 일정 · 모델을 저장해도 B 의 것은 그대로이고, B 의 조회는 B 의 값만
 * 낸다. A 가 구독 제안을 거절해도 B 의 거절은 비어 있다. 세션 없이 부르면 처리기 열둘 모두 401 이다(docs/qa/test-strategy.md §13).
 * <p>
 * 지키는 규칙: account-R6
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
public class InterestScopeTests {

	/** 시험이 만드는 소스의 프리셋 키 — 프리셋 목록에 없어 기록된 이름이 없는 고를 소스가 된다 */
	private static final String SOURCE_KEY = "interest-scope";

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
	TopicSuggestionDeclines declines;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		UserTables.deleteAll(this.jdbc);
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
		this.jdbc.sql("delete from source_attributions where source_id in (select id from sources where preset_key = :key)")
			.param("key", SOURCE_KEY)
			.update();
		this.jdbc.sql("delete from sources where preset_key = :key").param("key", SOURCE_KEY).update();
	}

	@Test
	void userAChangesOnlyTheirOwnInterests() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("interest-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("interest-b@example.com");
		LlmKeyRows.give(this.jdbc, a.id().value());
		long tagSet = this.tagSets.activeTagSet().id().value();
		long source = this.jdbc
			.sql("insert into sources (preset_key, enabled, version, created_at) values (:key, false, 0, now()) returning id")
			.param("key", SOURCE_KEY)
			.query(Long.class)
			.single();

		this.mockMvc.perform(json(put("/api/interests/tags").with(a), """
				{"tagSetId":%d,"topics":["politics"],"excludedRegions":["us"],"excludedFormats":[]}""".formatted(tagSet)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("SAVED"));
		this.mockMvc.perform(json(put("/api/interests/settings").with(a), """
				{"timezone":"Europe/Paris","digestTime":"06:15","interestText":"반도체","weeklyReport":false,
				 "weeklyReportDay":"FRIDAY","monthlyReport":false}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("SAVED"));
		this.mockMvc.perform(json(put("/api/interests/model").with(a), """
				{"provider":"OPENAI","modelId":"gpt-6-astra"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("SAVED"));
		this.mockMvc.perform(json(put("/api/interests/effort").with(a), """
				{"effort":"HIGH"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.effort").value("HIGH"));
		this.mockMvc.perform(json(put("/api/interests/volume").with(a), """
				{"volume":"CUSTOM","customCount":45,"bigSlots":0,"summaryLength":"DETAILED"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("SAVED"));
		this.mockMvc.perform(json(put("/api/interests/sources").with(a), """
				{"excluded":[%d]}""".formatted(source)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.excluded[0]").value(source));

		this.mockMvc.perform(get("/api/interests/tags").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.preferences.topics").isEmpty())
			.andExpect(jsonPath("$.preferences.excludedRegions").isEmpty());
		this.mockMvc.perform(get("/api/interests/settings").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
			.andExpect(jsonPath("$.interestText").value(""));
		this.mockMvc.perform(get("/api/interests/model").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.chosen").doesNotExist())
			.andExpect(jsonPath("$.choosable").isEmpty())
			.andExpect(jsonPath("$.effort").value("MEDIUM"));
		this.mockMvc.perform(get("/api/interests/volume").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.volume").value("NORMAL"))
			.andExpect(jsonPath("$.bigSlots").value(3))
			.andExpect(jsonPath("$.summaryLength").value("NORMAL"));
		this.mockMvc.perform(get("/api/interests/sources").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sources[*].id").value(hasItem((int) source)))
			.andExpect(jsonPath("$.excluded").isEmpty());
		assertThat(this.jdbc.sql("select count(*) from user_tag_preferences where user_id = :b and tag_kind <> 'FORMAT'")
			.param("b", b.id().value())
			.query(Long.class)
			.single()).as("A 의 저장은 B 의 선호를 바꾸지 않는다").isZero();
		this.mockMvc.perform(get("/api/interests/settings").with(a))
			.andExpect(jsonPath("$.timezone").value("Europe/Paris"));
		this.mockMvc.perform(get("/api/interests/volume").with(a))
			.andExpect(jsonPath("$.customCount").value(45))
			.andExpect(jsonPath("$.bigSlots").value(0))
			.andExpect(jsonPath("$.summaryLength").value("DETAILED"));
		this.mockMvc.perform(get("/api/interests/model").with(a)).andExpect(jsonPath("$.effort").value("HIGH"));
	}

	@Test
	void aDeclineByUserAStaysWithUserA() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("decline-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("decline-b@example.com");
		long tagSet = this.tagSets.activeTagSet().id().value();

		this.mockMvc.perform(json(post("/api/interests/declined-suggestions").with(a), """
				{"tagSetId":%d,"topic":"economy.markets"}""".formatted(tagSet)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("DECLINED"));

		assertThat(this.declines.declinesOf(a.id()).declines()).singleElement()
			.satisfies(decline -> assertThat(decline.subtopic()).isEqualTo("economy.markets"));
		assertThat(this.declines.declinesOf(b.id()).declines()).isEmpty();
	}

	@Test
	void interestPathsNeedASession() throws Exception {
		Cookie csrf = new Cookie("XSRF-TOKEN", "interest-scope");
		for (String path : new String[] { "/api/interests/tags", "/api/interests/sources", "/api/interests/settings",
				"/api/interests/model", "/api/interests/volume" }) {
			this.mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
			this.mockMvc
				.perform(json(put(path).cookie(csrf).header("X-XSRF-TOKEN", "interest-scope"), "{}"))
				.andExpect(status().isUnauthorized());
		}
		this.mockMvc
			.perform(json(put("/api/interests/effort").cookie(csrf).header("X-XSRF-TOKEN", "interest-scope"),
					"{\"effort\":\"LOW\"}"))
			.andExpect(status().isUnauthorized());
		this.mockMvc
			.perform(json(post("/api/interests/declined-suggestions").cookie(csrf)
				.header("X-XSRF-TOKEN", "interest-scope"), "{\"tagSetId\":1,\"topic\":\"economy.markets\"}"))
			.andExpect(status().isUnauthorized());
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
