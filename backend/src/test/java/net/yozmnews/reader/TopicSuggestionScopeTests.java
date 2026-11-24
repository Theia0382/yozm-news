package net.yozmnews.reader;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.SignedInUsers;
import net.yozmnews.user.UserTables;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * 구독 제안의 사용자 격리 — 사용자 A 의 좋아요로 생긴 제안은 A 의 응답에만 있고 B 의 응답에는 없다. A 와 B 가 같은 주제에 같은 신호를 가진 뒤 A 가
 * 거절하면 A 의 제안에서만 빠진다. 세션 없이 401, 키가 없으면 403(키 관문)이다(docs/qa/test-strategy.md §13).
 * <p>
 * 지키는 규칙: user-R5 · user-R6 · reader-R12
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
public class TopicSuggestionScopeTests {

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

	private InterestRows rows;

	@BeforeEach
	@AfterEach
	void reset() {
		this.rows = new InterestRows(this.jdbc);
		this.rows.clear();
		UserTables.deleteAll(this.jdbc);
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void aSuggestionFromUserASignalsReachesOnlyUserA() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("suggestion-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("suggestion-b@example.com");
		LlmKeyRows.give(this.jdbc, a.id().value());
		LlmKeyRows.give(this.jdbc, b.id().value());
		likedPair(a, "politics.legislature");

		// 빈 응답끼리 같아 통과하지 않게 A 의 응답에 행이 있음을 먼저 본다
		this.mockMvc.perform(get("/api/topic-suggestions").with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.topics[0].key").value("politics.legislature"));
		this.mockMvc.perform(get("/api/topic-suggestions").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.topics").isEmpty());
	}

	@Test
	void aDeclineByUserARemovesTheSuggestionOnlyForUserA() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("suggestion-decline-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("suggestion-decline-b@example.com");
		LlmKeyRows.give(this.jdbc, a.id().value());
		LlmKeyRows.give(this.jdbc, b.id().value());
		likedPair(a, "politics.legislature");
		likedPair(b, "politics.legislature");
		this.mockMvc.perform(get("/api/topic-suggestions").with(b))
			.andExpect(jsonPath("$.topics[0].key").value("politics.legislature"));

		this.mockMvc.perform(post("/api/interests/declined-suggestions").with(a)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"tagSetId":%d,"topic":"politics.legislature"}""".formatted(this.tagSets.activeTagSet().id().value())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("DECLINED"));

		this.mockMvc.perform(get("/api/topic-suggestions").with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.topics").isEmpty());
		this.mockMvc.perform(get("/api/topic-suggestions").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.topics[0].key").value("politics.legislature"));
	}

	@Test
	void thePathNeedsASessionAndAKey() throws Exception {
		SignedInUsers.SignedInUser keyless = new SignedInUsers(this.mockMvc, this.accounts, this.credentials,
				this.hashing)
			.signIn("suggestion-keyless@example.com");

		this.mockMvc.perform(get("/api/topic-suggestions")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/topic-suggestions").with(keyless))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.type").value("urn:yozmnews:problem:llm-key-required"));
	}

	/** 하루 전 다이제스트가 그 주제의 기사 둘을 보였고 둘 다 좋아요 — 관심 약 0.394 */
	private void likedPair(SignedInUsers.SignedInUser user, String topic) {
		Instant shown = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofDays(1));
		long first = this.rows.article("첫 기사", topic);
		long second = this.rows.article("둘째 기사", topic);
		this.rows.cite(user.id().value(), shown, first, second);
		this.rows.react(user.id().value(), first, "LIKE", shown.plus(Duration.ofMinutes(5)));
		this.rows.react(user.id().value(), second, "LIKE", shown.plus(Duration.ofMinutes(5)));
	}

}
