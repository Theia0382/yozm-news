package net.yozmnews.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.DigestRows;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 좋아요 / 싫어요의 사용자 격리 — A 가 남긴 값은 B 의 현재 상태에 없고, B 는 A 의 다이제스트로 누르지 못하며 A 의 행이 그대로다. 세션 없이
 * 부르면 두 경로 모두 401 이고, 키가 없는 세션은 403 이다(docs/qa/test-strategy.md §13). 키 관문 뒤라 A · B 는 키 행이 있다.
 * <p>
 * 지키는 규칙: user-R5 · user-R6 · feedback-R2 · feedback-R9
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
public class FeedbackScopeTests {

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
		FeedbackRows.clear(this.jdbc);
		this.jdbc.sql("delete from users").update();
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void userAReadsAndChangesOnlyTheirOwnFeedback() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("feedback-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("feedback-b@example.com");
		LlmKeyRows.give(this.jdbc, a.id().value());
		LlmKeyRows.give(this.jdbc, b.id().value());
		long article = FeedbackRows.article(this.jdbc, "격리 시험 기사");
		long digestA = DigestRows.digest(this.jdbc, a.id().value(), "2026-11-05");
		DigestRows.cite(this.jdbc, digestA, article);
		long digestB = DigestRows.digest(this.jdbc, b.id().value(), "2026-11-05");
		DigestRows.cite(this.jdbc, digestB, article);

		this.mockMvc.perform(press(a, article, "LIKE", digestA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("RECORDED"));
		// A 의 현재 상태에 행이 있다 — 아래 B 의 빈 목록이 빈 본문끼리의 일치가 아니다
		this.mockMvc.perform(get("/api/feedback").param("articleIds", String.valueOf(article)).with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.states.length()").value(1))
			.andExpect(jsonPath("$.states[0].value").value("LIKE"));

		this.mockMvc.perform(get("/api/feedback").param("articleIds", String.valueOf(article)).with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.states").isEmpty());
		// B 가 A 의 다이제스트로 누르면 받지 않고, A 의 행은 그대로다
		this.mockMvc.perform(press(b, article, "NONE", digestA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("NOT_IN_DIGEST"));
		this.mockMvc.perform(press(b, article, "DISLIKE", digestA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("NOT_IN_DIGEST"));
		assertThat(FeedbackRows.current(this.jdbc, a.id().value())).containsExactly(article + ":LIKE:" + digestA);
		assertThat(FeedbackRows.current(this.jdbc, b.id().value())).isEmpty();

		// B 가 자기 다이제스트로 남긴 값은 B 의 것이다 — A 의 값을 바꾸지 않는다
		this.mockMvc.perform(press(b, article, "DISLIKE", digestB))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("RECORDED"));
		this.mockMvc.perform(get("/api/feedback").param("articleIds", String.valueOf(article)).with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.states[0].value").value("LIKE"));
	}

	@Test
	void feedbackPathsNeedASessionAndAKey() throws Exception {
		SignedInUsers.SignedInUser keyless = new SignedInUsers(this.mockMvc, this.accounts, this.credentials,
				this.hashing)
			.signIn("feedback-keyless@example.com");
		this.mockMvc.perform(get("/api/feedback").param("articleIds", "1").with(keyless))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.type").value("urn:yozmnews:problem:llm-key-required"));
		this.mockMvc.perform(press(keyless, 1, "LIKE", 1))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.type").value("urn:yozmnews:problem:llm-key-required"));

		Cookie csrf = new Cookie("XSRF-TOKEN", "feedback-scope");
		this.mockMvc.perform(get("/api/feedback").param("articleIds", "1")).andExpect(status().isUnauthorized());
		this.mockMvc
			.perform(put("/api/feedback/1").cookie(csrf)
				.header("X-XSRF-TOKEN", "feedback-scope")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"value":"LIKE","digestId":1}"""))
			.andExpect(status().isUnauthorized());
	}

	private static MockHttpServletRequestBuilder press(
			SignedInUsers.SignedInUser user, long article, String value, long digest) {
		return put("/api/feedback/{articleId}", article).with(user)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"value":"%s","digestId":%d}""".formatted(value, digest));
	}

}
