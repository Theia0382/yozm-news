package net.yozmnews.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.servlet.http.Cookie;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.user.UserId;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.application.SignIn;
import net.yozmnews.user.application.SignInOutcome;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.credential.PasswordCredential;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.domain.throttle.EmailDigest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 로그인 API — 실제 PostgreSQL 위에서 로그인 · 거절 · 대기의 판정 결과, 계정 유무와 무관한 같은 본문, 실패 기록, 로그인 상태 조회, 로그아웃을
 * 본다.
 * <p>
 * 지키는 규칙: user-R10 · user-R11 · user-R17 · user-R18 · user-R19 · user-R20
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerTests {

	private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

	private static final String PASSWORD = "correct horse battery";

	private static final String CSRF = "session-controller-tests";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	SignIn signIn;

	@Autowired
	JdbcClient jdbc;

	@Test
	void rightPasswordSignsInAndTheStateShowsTheAccount() throws Exception {
		long user = account("alice@example.com", AccountRole.ADMIN);

		MockHttpServletResponse response = this.mockMvc
			.perform(signIn("Alice@Example.com", PASSWORD, false))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"SIGNED_IN","retryAfterSeconds":null}
					""", JsonCompareMode.STRICT))
			.andReturn()
			.getResponse();
		Cookie session = sessionCookie(response);

		this.mockMvc.perform(get("/api/me").cookie(session))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"SIGNED_IN","userId":%d,"email":"alice@example.com","displayName":"시험","role":"ADMIN",
					 "passwordChangeRequired":false}
					""".formatted(user), JsonCompareMode.STRICT));
	}

	@Test
	void anonymousStateIsAResultNotAnError() throws Exception {
		this.mockMvc.perform(get("/api/me"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"ANONYMOUS","userId":null,"email":null,"displayName":null,"role":null,
					 "passwordChangeRequired":false}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void accountThatMustChangeThePasswordSignsInAndTheStateSaysSo() throws Exception {
		long user = account("initial@example.com", AccountRole.USER, true);
		Cookie session = sessionCookie(this.mockMvc.perform(signIn("initial@example.com", PASSWORD, false))
			.andExpect(content().json("""
					{"result":"SIGNED_IN","retryAfterSeconds":null}
					""", JsonCompareMode.STRICT))
			.andReturn()
			.getResponse());

		this.mockMvc.perform(get("/api/me").cookie(session))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"SIGNED_IN","userId":%d,"email":"initial@example.com","displayName":"시험","role":"USER",
					 "passwordChangeRequired":true}
					""".formatted(user), JsonCompareMode.STRICT));
		this.mockMvc.perform(withCsrf(delete("/api/session")).cookie(session)).andExpect(status().isNoContent());
	}

	@Test
	void wrongPasswordMissingAccountAndInactiveAccountGiveTheSameBody() throws Exception {
		long inactive = account("carol@example.com", AccountRole.USER);
		account("bob@example.com", AccountRole.USER);
		this.jdbc.sql("update users set status = 'SUSPENDED' where id = :id").param("id", inactive).update();

		String wrong = body(signIn("bob@example.com", "wrong horse battery", false));
		String missing = body(signIn("nobody@example.com", "wrong horse battery", false));
		String suspended = body(signIn("carol@example.com", PASSWORD, false));

		assertThat(wrong).isEqualTo("""
				{"result":"REJECTED","retryAfterSeconds":null}""");
		assertThat(missing).isEqualTo(wrong);
		assertThat(suspended).isEqualTo(wrong);
		assertThat(failures("nobody@example.com")).as("없는 이메일도 실패를 센다").isEqualTo(1);
	}

	@Test
	void waitingRefusesEvenTheRightPassword() throws Exception {
		account("dave@example.com", AccountRole.USER);
		// 연속 실패 10회 — 대기는 1초 × 2^6 = 64초다
		this.jdbc.sql("""
				insert into login_failures (attempt, email_digest, failures, last_failed_at)
				values ('SIGN_IN', :digest, 10, :at)
				""")
			.param("digest", EmailDigest.of("dave@example.com").value())
			.param("at", Timestamp.from(Instant.now()))
			.update();

		// 넣은 뒤 요청까지 걸린 시간만큼 줄 수 있다 — 올림 규칙 자체는 SignInResponseTests 가 본다
		this.mockMvc.perform(signIn("dave@example.com", PASSWORD, false))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("WAIT"))
			.andExpect(jsonPath("$.retryAfterSeconds", allOf(greaterThanOrEqualTo(63), lessThanOrEqualTo(64))));
		assertThat(failures("dave@example.com")).as("대기 중 시도는 실패로 세지 않는다").isEqualTo(10);
	}

	@Test
	void fourthFailureMakesTheNextAttemptWait() throws Exception {
		account("hank@example.com", AccountRole.USER);
		for (int i = 0; i < 4; i++) {
			assertThat(body(signIn("hank@example.com", "wrong horse battery", false))).isEqualTo("""
					{"result":"REJECTED","retryAfterSeconds":null}""");
		}

		this.mockMvc.perform(signIn("hank@example.com", PASSWORD, false))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"WAIT","retryAfterSeconds":1}
					""", JsonCompareMode.STRICT));
		assertThat(failures("hank@example.com")).isEqualTo(4);
	}

	@Test
	void successForgetsTheFailures() throws Exception {
		account("erin@example.com", AccountRole.USER);
		this.mockMvc.perform(signIn("erin@example.com", "wrong horse battery", false)).andExpect(status().isOk());
		this.mockMvc.perform(signIn("someone-else@example.com", "wrong horse battery", false)).andExpect(status().isOk());
		assertThat(failures("erin@example.com")).isEqualTo(1);

		this.mockMvc.perform(signIn("erin@example.com", PASSWORD, false))
			.andExpect(content().json("""
					{"result":"SIGNED_IN","retryAfterSeconds":null}
					""", JsonCompareMode.STRICT));
		assertThat(rows("erin@example.com")).isZero();
		assertThat(failures("someone-else@example.com")).as("다른 이메일의 기록은 그대로다").isEqualTo(1);
	}

	@Test
	void concurrentAttemptsOnOneEmailAreCountedOneByOne() throws Exception {
		account("frank@example.com", AccountRole.USER);
		ExecutorService pool = Executors.newFixedThreadPool(3);
		try {
			List<Callable<SignInOutcome>> attempts = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				attempts.add(() -> this.signIn.attempt("frank@example.com", "wrong horse battery", Instant.now()));
			}
			List<SignInOutcome> outcomes = new ArrayList<>();
			for (Future<SignInOutcome> outcome : pool.invokeAll(attempts)) {
				outcomes.add(outcome.get());
			}

			assertThat(outcomes).allMatch(SignInOutcome.Rejected.class::isInstance);
			assertThat(failures("frank@example.com")).as("잃은 갱신이 없다").isEqualTo(3);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void signInNeedsTheCsrfTokenAndAllFields() throws Exception {
		this.mockMvc
			.perform(post("/api/session").contentType(MediaType.APPLICATION_JSON).content("""
					{"email":"a@b.c","password":"x","keep":false}"""))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(withCsrf(post("/api/session")).contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"a@b.c","password":"x"}"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void signOutEndsTheSessionAndAnswersNoContentWithoutOne() throws Exception {
		account("gina@example.com", AccountRole.USER);
		Cookie session = sessionCookie(this.mockMvc.perform(signIn("gina@example.com", PASSWORD, true))
			.andReturn()
			.getResponse());

		this.mockMvc.perform(withCsrf(delete("/api/session")).cookie(session)).andExpect(status().isNoContent());
		this.mockMvc.perform(get("/api/me").cookie(session))
			.andExpect(content().json("""
					{"result":"ANONYMOUS","userId":null,"email":null,"displayName":null,"role":null,
					 "passwordChangeRequired":false}
					""", JsonCompareMode.STRICT));
		this.mockMvc.perform(withCsrf(delete("/api/session"))).andExpect(status().isNoContent());
	}

	private long account(String email, AccountRole role) {
		return account(email, role, false);
	}

	private long account(String email, AccountRole role, boolean mustChange) {
		UserId id = this.accounts.save(Account.create(EmailAddress.parse(email).orElseThrow(),
				new DisplayName("시험"), role, NOW)).id();
		this.credentials.save(PasswordCredential.of(id, this.hashing.hash(PASSWORD), mustChange, NOW));
		return id.value();
	}

	private static MockHttpServletRequestBuilder signIn(String email, String password, boolean keep) {
		return withCsrf(post("/api/session")).contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","password":"%s","keep":%s}""".formatted(email, password, keep));
	}

	private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
		return request.cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF);
	}

	private String body(MockHttpServletRequestBuilder request) throws Exception {
		return this.mockMvc.perform(request)
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private int failures(String normalizedEmail) {
		return this.jdbc.sql("select failures from login_failures where email_digest = :digest")
			.param("digest", EmailDigest.of(normalizedEmail).value())
			.query(Integer.class)
			.single();
	}

	private int rows(String normalizedEmail) {
		return this.jdbc.sql("select count(*) from login_failures where email_digest = :digest")
			.param("digest", EmailDigest.of(normalizedEmail).value())
			.query(Integer.class)
			.single();
	}

	private static Cookie sessionCookie(MockHttpServletResponse response) {
		String header = response.getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(value -> value.startsWith("SESSION="))
			.reduce((a, b) -> b)
			.orElseThrow();
		String pair = header.split(";", 2)[0];
		return new Cookie("SESSION", pair.substring(pair.indexOf('=') + 1));
	}

}
