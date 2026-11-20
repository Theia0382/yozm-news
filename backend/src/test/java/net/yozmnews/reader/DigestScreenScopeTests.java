package net.yozmnews.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.DigestRounds;
import net.yozmnews.user.SignedInUsers;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import org.jobrunr.scheduling.JobScheduler;
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
 * 독자 화면 다이제스트의 사용자 격리 — A 의 다이제스트는 B 의 세션으로 홈 · 날짜 · 날짜 목록 어디서도 보이지 않고, B 의 수동 생성 요청은 B 의 행만
 * 만든다. 네 경로 모두 세션 없이 401, 키가 없으면 403(키 관문)이다(docs/qa/test-strategy.md §13). 생성 작업은 돌지 않는다(작업 서버 끔).
 * <p>
 * 지키는 규칙: user-R5 · user-R6 · reader-R9 ~ reader-R11
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
public class DigestScreenScopeTests {

	@Autowired
	JobScheduler digestJobs;

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

	private DigestScreenRows rows;

	private ReaderRows readerRows;

	@BeforeEach
	void stopDigestRounds() {
		DigestRounds.stop(this.digestJobs);
	}

	@BeforeEach
	@AfterEach
	void reset() {
		this.rows = new DigestScreenRows(this.jdbc);
		this.readerRows = new ReaderRows(this.jdbc);
		this.rows.clear();
		this.readerRows.clear();
		this.jdbc.sql("delete from users").update();
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void aUsersDigestIsSeenOnlyThroughTheirOwnSession() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("digest-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("digest-b@example.com");
		LlmKeyRows.give(this.jdbc, a.id().value());
		LlmKeyRows.give(this.jdbc, b.id().value());
		long article = this.readerRows.article("격리 시험 기사");
		long digest = this.rows.digest(a.id().value(), "2026-11-05", "READY", 100, 20, null);
		this.rows.cite(this.rows.section(digest, "HEADLINE", 0, "헤드라인", null), 0, article);

		// 빈 본문끼리 같아 통과하지 않게 A 의 세션으로 A 의 다이제스트가 보임을 먼저 본다
		this.mockMvc.perform(get("/api/digests/latest").with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.day.date").value("2026-11-05"))
			.andExpect(jsonPath("$.day.shown.digestId").value(digest))
			.andExpect(jsonPath("$.day.shown.sections[0].citations[0].articleId").value(article));
		this.mockMvc.perform(get("/api/digests/2026-11-05").with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shown.digestId").value(digest));
		this.mockMvc.perform(get("/api/digests").with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dates[0].date").value("2026-11-05"));

		this.mockMvc.perform(get("/api/digests/latest").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.day").isEmpty());
		this.mockMvc.perform(get("/api/digests/2026-11-05").with(b)).andExpect(status().isNotFound());
		this.mockMvc.perform(get("/api/digests").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dates").isEmpty());
	}

	@Test
	void aUsersUnresolvedFailureIsSeenOnlyThroughTheirOwnSession() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("digest-failure-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("digest-failure-b@example.com");
		LlmKeyRows.give(this.jdbc, a.id().value());
		LlmKeyRows.give(this.jdbc, b.id().value());
		this.rows.digest(a.id().value(), "2026-11-05", "FAILED", null, null, "PROVIDER_UNAVAILABLE");

		// 빈 값끼리 같아 통과하지 않게 A 의 세션으로 A 의 실패가 보임을 먼저 본다
		this.mockMvc.perform(get("/api/digests/latest").with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status.unresolvedFailure.digestDate").value("2026-11-05"));
		this.mockMvc.perform(get("/api/digests/latest").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status.unresolvedFailure").isEmpty());
	}

	@Test
	void aRequestMakesOnlyTheRequestersRow() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("digest-request-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("digest-request-b@example.com");
		LlmKeyRows.give(this.jdbc, a.id().value());
		LlmKeyRows.give(this.jdbc, b.id().value());
		this.rows.digest(a.id().value(), "2026-11-05", "EMPTY", null, null, null);

		// B 에게 쓸 모델이 있어 요청이 받아지고 B 의 행이 생긴다 — 받지 않으면 A 의 행이 그대로인 것이 늘 참이다
		this.mockMvc.perform(post("/api/digests").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("REQUESTED"));
		assertThat(this.rows.count(b.id().value())).isEqualTo(1);
		assertThat(this.rows.count(a.id().value())).isEqualTo(1);
	}

	@Test
	void everyPathNeedsASessionAndAKey() throws Exception {
		SignedInUsers.SignedInUser keyless = new SignedInUsers(this.mockMvc, this.accounts, this.credentials,
				this.hashing)
			.signIn("digest-keyless@example.com");

		for (String path : new String[] { "/api/digests/latest", "/api/digests/2026-11-05", "/api/digests" }) {
			this.mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
			this.mockMvc.perform(get(path).with(keyless))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type").value("urn:yozmnews:problem:llm-key-required"));
		}
		Cookie csrf = new Cookie("XSRF-TOKEN", "digest-scope");
		this.mockMvc.perform(post("/api/digests").cookie(csrf).header("X-XSRF-TOKEN", "digest-scope"))
			.andExpect(status().isUnauthorized());
		this.mockMvc.perform(post("/api/digests").with(keyless))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.type").value("urn:yozmnews:problem:llm-key-required"));
		assertThat(this.rows.count(keyless.id().value())).isZero();
	}

}
