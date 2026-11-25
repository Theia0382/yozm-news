package net.yozmnews.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.ZoneOffset;
import net.yozmnews.account.SignedInUsers;

import jakarta.servlet.http.Cookie;
import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.application.PasswordHashing;
import net.yozmnews.account.domain.account.AccountRepository;
import net.yozmnews.account.domain.credential.PasswordCredentialRepository;
import net.yozmnews.storyline.StorylineRows;
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
 * 스토리라인 구독의 사용자 격리 — 사용자 A 의 구독은 B 의 목록에 없고, B 의 해제는 A 의 구독을 지우지 않으며, 구독 이력은 자기 행만
 * 낸다. 키가 없는 사용자도 쓴다. 세션 없이 부르면 네 경로 모두 401 이다(docs/qa/test-strategy.md §13).
 * <p>
 * 지키는 규칙: account-R5 · account-R6 · user-R52 · user-R63
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
public class StorylineSubscriptionScopeTests {

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

	@BeforeEach
	@AfterEach
	void reset() {
		UserTables.deleteAll(this.jdbc);
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
		StorylineRows.clear(this.jdbc);
	}

	@Test
	void userAChangesOnlyTheirOwnSubscriptions() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("subscription-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("subscription-b@example.com");
		// A 만 키가 있다 — 키가 없는 B 도 구독 경로를 쓴다
		LlmKeyRows.give(this.jdbc, a.id().value());
		long storyline = storyline();

		this.mockMvc.perform(put("/api/storyline-subscriptions/{id}", storyline).with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("SUBSCRIBED"));

		this.mockMvc.perform(get("/api/storyline-subscriptions").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.subscriptions").isEmpty());
		// A 가 추적한 스토리라인을 B 의 세션으로 물으면 추적 중이 아니다 — 대조: A 의 세션으로는 추적 중이다
		this.mockMvc.perform(get("/api/storyline-subscriptions/states").param("ids", String.valueOf(storyline)).with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.states[0].storylineId").value(storyline))
			.andExpect(jsonPath("$.states[0].tracked").value(false));
		this.mockMvc.perform(get("/api/storyline-subscriptions/states").param("ids", String.valueOf(storyline)).with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.states[0].tracked").value(true));
		this.mockMvc.perform(delete("/api/storyline-subscriptions/{id}", storyline).with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("NOT_SUBSCRIBED"));
		assertThat(this.jdbc.sql("select count(*) from user_storyline_subscriptions where user_id = :a")
			.param("a", a.id().value())
			.query(Long.class)
			.single()).as("B 의 해제는 A 의 구독을 지우지 않는다").isOne();
		// 키가 없는 B 의 구독은 B 의 것이다 — A 의 목록에 들지 않는다
		long other = storyline();
		this.mockMvc.perform(put("/api/storyline-subscriptions/{id}", other).with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("SUBSCRIBED"));
		this.mockMvc.perform(get("/api/storyline-subscriptions").with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.subscriptions.length()").value(1))
			.andExpect(jsonPath("$.subscriptions[0].storylineIds[0]").value(storyline));
	}

	@Test
	void eachUserReadsOnlyTheirOwnHistory() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("history-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("history-b@example.com");
		// A 만 키가 있다 — 키가 없는 B 도 구독 이력을 읽는다(키 관문 403 이 아니다)
		LlmKeyRows.give(this.jdbc, a.id().value());
		long ofA = storyline();
		long ofB = storyline();
		this.mockMvc.perform(put("/api/storyline-subscriptions/{id}", ofA).with(a)).andExpect(status().isOk());
		this.mockMvc.perform(put("/api/storyline-subscriptions/{id}", ofB).with(b)).andExpect(status().isOk());

		this.mockMvc.perform(get("/api/storyline-subscriptions/changes").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.changes.length()").value(1))
			.andExpect(jsonPath("$.changes[0].storylineId").value(ofB));
		this.mockMvc.perform(get("/api/storyline-subscriptions/changes").with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.changes.length()").value(1))
			.andExpect(jsonPath("$.changes[0].storylineId").value(ofA));
	}

	@Test
	void subscriptionPathsNeedASession() throws Exception {
		Cookie csrf = new Cookie("XSRF-TOKEN", "subscription-scope");
		this.mockMvc.perform(get("/api/storyline-subscriptions")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/storyline-subscriptions/changes")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/storyline-subscriptions/states").param("ids", "1"))
			.andExpect(status().isUnauthorized());
		this.mockMvc
			.perform(put("/api/storyline-subscriptions/1").cookie(csrf).header("X-XSRF-TOKEN", "subscription-scope"))
			.andExpect(status().isUnauthorized());
		this.mockMvc
			.perform(delete("/api/storyline-subscriptions/1").cookie(csrf).header("X-XSRF-TOKEN", "subscription-scope"))
			.andExpect(status().isUnauthorized());
	}

	private long storyline() {
		return this.jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				                        representative_event_id, created_at, updated_at)
				values ('model-a', cast('[1,0]' as halfvec), 1, :at, :at, 1, :at, :at)
				returning id
				""").param("at", Instant.now().atOffset(ZoneOffset.UTC)).query(Long.class).single();
	}

}
