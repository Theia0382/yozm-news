package net.yozmnews.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.user.SignedInUsers;
import net.yozmnews.user.UserStorylineFixtures;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 정리된 사용자 스토리라인 화면의 사용자 격리 — 사용자 A 의 정리는 A 의 목록에만 있고, B 의 세션으로 A 의 사용자 스토리라인 ID 를 부르면 없는
 * 것(404)이다. 같은 흐름의 다이제스트 항목도 자기 것만 본다. 세션 없이 401, 키가 없으면 403(키 관문)이다(docs/qa/test-strategy.md §13).
 * <p>
 * 지키는 규칙: user-R5 · user-R6 · docs/specs/reader.md 의 사용자 스토리라인 화면
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
public class UserStorylineScreenScopeTests {

	private static final Instant AT = Instant.parse("2026-11-20T00:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	JdbcClient jdbc;

	private UserStorylineSummaryRows rows;

	private DigestScreenRows digests;

	@BeforeEach
	@AfterEach
	void reset() {
		this.rows = new UserStorylineSummaryRows(this.jdbc);
		this.digests = new DigestScreenRows(this.jdbc);
		this.digests.clear();
		this.jdbc.sql("delete from storylines").update();
		this.jdbc.sql("delete from users").update();
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void aUsersSummariesAndTheirItemsAreSeenOnlyThroughTheirOwnSession() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("user-storyline-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("user-storyline-b@example.com");
		LlmKeyRows.give(this.jdbc, a.id().value());
		LlmKeyRows.give(this.jdbc, b.id().value());
		long flow = UserStorylineFixtures.storyline(this.jdbc, AT);
		long ofA = this.rows.userStoryline(a.id().value(), flow);
		this.rows.summarize(ofA, "A 의 흐름", AT);
		long ofB = this.rows.userStoryline(b.id().value(), flow);
		this.rows.summarize(ofB, "B 의 흐름", AT);
		item(this.digests.digest(a.id().value(), "2026-11-05", "READY", 1, 1, null), "A 의 항목", flow);
		item(this.digests.digest(b.id().value(), "2026-11-05", "READY", 1, 1, null), "B 의 항목", flow);

		// 빈 응답끼리 같아 통과하지 않게 A 의 응답에 행이 있음을 먼저 본다
		this.mockMvc.perform(get("/api/user-storylines").with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userStorylines.length()").value(1))
			.andExpect(jsonPath("$.userStorylines[0].id").value(ofA))
			.andExpect(jsonPath("$.userStorylines[0].title").value("A 의 흐름 제목"));
		this.mockMvc.perform(get("/api/user-storylines/{id}", ofA).with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("A 의 흐름 제목"));
		this.mockMvc.perform(get("/api/user-storylines/{id}/items", ofA).with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].title").value("A 의 항목"));

		String listOfB = this.mockMvc.perform(get("/api/user-storylines").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userStorylines.length()").value(1))
			.andExpect(jsonPath("$.userStorylines[0].id").value(ofB))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(listOfB).doesNotContain("A 의 흐름");
		this.mockMvc.perform(get("/api/user-storylines/{id}", ofA).with(b)).andExpect(status().isNotFound());
		this.mockMvc.perform(get("/api/user-storylines/{id}/items", ofA).with(b)).andExpect(status().isNotFound());
		String itemsOfB = this.mockMvc.perform(get("/api/user-storylines/{id}/items", ofB).with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(itemsOfB).contains("B 의 항목").doesNotContain("A 의 항목");
	}

	@Test
	void everyPathNeedsASessionAndAKey() throws Exception {
		SignedInUsers.SignedInUser keyless = new SignedInUsers(this.mockMvc, this.accounts, this.credentials,
				this.hashing)
			.signIn("user-storyline-keyless@example.com");

		for (String path : new String[] { "/api/user-storylines", "/api/user-storylines/1",
				"/api/user-storylines/1/items" }) {
			this.mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
			this.mockMvc.perform(get(path).with(keyless))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type").value("urn:yozmnews:problem:llm-key-required"));
		}
	}

	private void item(long digest, String title, long flow) {
		long section = this.digests.section(digest, "TOP_STORY", 1, title, null);
		this.digests.sectionEvent(section, 0, 70, "SUBSCRIBED");
		this.jdbc.sql("update digest_sections set storyline_id = :flow where id = :id")
			.param("flow", flow)
			.param("id", section)
			.update();
	}

}
