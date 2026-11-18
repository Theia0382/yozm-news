package net.yozmnews.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 관심사 히스토리의 사용자 격리 — 사용자 A 의 노출 · 좋아요 · 주제 구독 변경은 A 의 응답에만 있고 B 의 응답에는 없다. 세션 없이 401, 키가 없으면
 * 403(키 관문)이다(docs/qa/test-strategy.md §13).
 * <p>
 * 지키는 규칙: user-R5 · user-R6 · reader-R8
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
public class InterestHistoryScopeTests {

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

	private InterestRows rows;

	@BeforeEach
	@AfterEach
	void reset() {
		this.rows = new InterestRows(this.jdbc);
		this.rows.clear();
		this.jdbc.sql("delete from users").update();
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void theHistoryHoldsOnlyTheSignalsOfTheSessionUser() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("history-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("history-b@example.com");
		LlmKeyRows.give(this.jdbc, a.id().value());
		LlmKeyRows.give(this.jdbc, b.id().value());
		Instant shown = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofDays(1));
		long article = this.rows.article("격리 시험 기사", "politics.executive");
		this.rows.cite(a.id().value(), shown, article);
		this.rows.react(a.id().value(), article, "LIKE", shown.plus(Duration.ofMinutes(5)));
		this.rows.subscribe(a.id().value(), "economy.markets", "ADD", shown);

		// 빈 응답끼리 같아 통과하지 않게 A 의 응답에 행이 있음을 먼저 본다
		this.mockMvc.perform(get("/api/interest-history").with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.topics[*].key").value(containsInAnyOrder("politics.executive", "economy.markets")));
		String bodyB = this.mockMvc.perform(get("/api/interest-history").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.periods.length()").value(13))
			.andExpect(jsonPath("$.topics").isEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(bodyB).doesNotContain("politics.executive").doesNotContain("economy.markets");
	}

	@Test
	void thePathNeedsASessionAndAKey() throws Exception {
		SignedInUsers.SignedInUser keyless = new SignedInUsers(this.mockMvc, this.accounts, this.credentials,
				this.hashing)
			.signIn("history-keyless@example.com");

		this.mockMvc.perform(get("/api/interest-history")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/interest-history").with(keyless))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.type").value("urn:yozmnews:problem:llm-key-required"));
	}

}
