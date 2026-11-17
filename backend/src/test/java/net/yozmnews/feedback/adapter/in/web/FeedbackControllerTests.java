package net.yozmnews.feedback.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Collectors;
import java.util.stream.LongStream;

import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.DigestRows;
import net.yozmnews.feedback.FeedbackRows;
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
 * 좋아요 / 싫어요 API — 판정 결과는 판별자 하나의 200 이고 손으로 쓴 JSON 과 STRICT 로 견준다. 꼴이 틀린 요청은 400 이다. 실제
 * PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: feedback-R2 · feedback-R3 · feedback-R7 · feedback-R9
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class FeedbackControllerTests {

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

	private SignedInUsers.SignedInUser user;

	@BeforeEach
	void signIn() throws Exception {
		reset();
		this.user = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing)
			.signIn("feedback@example.com");
		LlmKeyRows.give(this.jdbc, this.user.id().value());
	}

	@AfterEach
	void reset() {
		FeedbackRows.clear(this.jdbc);
		this.jdbc.sql("delete from users").update();
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void pressingAnswersEachOutcome() throws Exception {
		long article = FeedbackRows.article(this.jdbc, "처리기 시험 기사");
		long digest = DigestRows.digest(this.jdbc, this.user.id().value(), "2026-11-05");
		DigestRows.cite(this.jdbc, digest, article);

		this.mockMvc.perform(press(article, "LIKE", digest))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"RECORDED","value":"LIKE"}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(press(article, "LIKE", digest))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"UNCHANGED","value":"LIKE"}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(press(article, "NONE", digest))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"RECORDED","value":"NONE"}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(press(article, "DISLIKE", digest + 1000))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"NOT_IN_DIGEST","value":null}""", JsonCompareMode.STRICT));
	}

	@Test
	void malformedPressesAreBadRequests() throws Exception {
		this.mockMvc.perform(press(0, "LIKE", 1)).andExpect(status().isBadRequest());
		this.mockMvc.perform(press(1, "LIKE", 0)).andExpect(status().isBadRequest());
		this.mockMvc.perform(withBody(put("/api/feedback/abc"), """
				{"value":"LIKE","digestId":1}""")).andExpect(status().isBadRequest());
		this.mockMvc.perform(withBody(put("/api/feedback/1"), """
				{"value":"LOVE","digestId":1}""")).andExpect(status().isBadRequest());
		this.mockMvc.perform(withBody(put("/api/feedback/1"), """
				{"digestId":1}""")).andExpect(status().isBadRequest());
		this.mockMvc.perform(withBody(put("/api/feedback/1"), """
				{"value":"LIKE"}""")).andExpect(status().isBadRequest());
		// 대조 — 꼴이 맞으면 판정 결과 200 이다
		this.mockMvc.perform(press(1, "LIKE", 1)).andExpect(status().isOk());
	}

	@Test
	void statesListOnlyArticlesWithAStateInArticleOrder() throws Exception {
		long first = FeedbackRows.article(this.jdbc, "현재 상태 기사 하나");
		long second = FeedbackRows.article(this.jdbc, "현재 상태 기사 둘");
		long none = FeedbackRows.article(this.jdbc, "현재 상태 기사 셋");
		long digest = DigestRows.digest(this.jdbc, this.user.id().value(), "2026-11-05");
		DigestRows.cite(this.jdbc, digest, first);
		DigestRows.cite(this.jdbc, digest, second);
		DigestRows.cite(this.jdbc, digest, none);
		this.mockMvc.perform(press(second, "DISLIKE", digest)).andExpect(status().isOk());
		this.mockMvc.perform(press(first, "LIKE", digest)).andExpect(status().isOk());

		this.mockMvc
			.perform(get("/api/feedback").param("articleIds", second + "," + none + "," + first + "," + second)
				.with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"states":[{"articleId":%d,"value":"LIKE"},{"articleId":%d,"value":"DISLIKE"}]}"""
				.formatted(first, second), JsonCompareMode.STRICT));
	}

	@Test
	void malformedStateQueriesAreBadRequests() throws Exception {
		this.mockMvc.perform(get("/api/feedback").with(this.user)).andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/feedback").param("articleIds", "").with(this.user))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/feedback").param("articleIds", "1,0").with(this.user))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/feedback").param("articleIds", ids(501)).with(this.user))
			.andExpect(status().isBadRequest());
		// 대조 — 겹친 ID 를 섞어 서로 다른 500 개는 받는다
		this.mockMvc.perform(get("/api/feedback").param("articleIds", ids(500) + ",1,2").with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"states":[]}""", JsonCompareMode.STRICT));
	}

	private MockHttpServletRequestBuilder press(long article, String value, long digest) {
		return withBody(put("/api/feedback/{articleId}", article), """
				{"value":"%s","digestId":%d}""".formatted(value, digest));
	}

	private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, String body) {
		return request.with(this.user).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static String ids(int count) {
		return LongStream.rangeClosed(1, count).mapToObj(String::valueOf).collect(Collectors.joining(","));
	}

}
