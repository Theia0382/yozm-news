package net.yozmnews.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.Cookie;
import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.YozmnewsApplication;
import net.yozmnews.account.domain.account.Account;
import net.yozmnews.account.domain.account.AccountRepository;
import net.yozmnews.account.domain.account.AccountRole;
import net.yozmnews.account.domain.account.DisplayName;
import net.yozmnews.account.domain.account.EmailAddress;
import net.yozmnews.account.domain.credential.PasswordCredential;
import net.yozmnews.account.domain.credential.PasswordCredentialRepository;
import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 체인 — 실제 PostgreSQL 의 Spring Session 위에서 운영자 경로를 운영자 계정 세션 · CSRF 와 스크립트용 운영자 토큰으로 연다. 세션은
 * 세션 체인 시험의 시험 처리기({@link SessionChainTests.SessionProbe})가 {@link SessionSignIn} 을 직접 불러 만든다. 운영자 토큰을
 * 설정한 문맥이다.
 * <p>
 * 지키는 규칙: account-R1 · account-R3 · account-R5 · account-R7 · account-R10 · account-R22
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
		classes = { YozmnewsApplication.class, SessionChainTests.SessionProbe.class, AdminChainTests.AdminProbe.class },
		properties = "yozmnews.admin.token=" + AdminChainTests.TOKEN)
@AutoConfigureMockMvc
class AdminChainTests {

	static final String TOKEN = "7d2b9e4a1c6f30d8b5e2a7c4f19d0e6b3a8c5f2d7e1b4a09";

	private static final Instant NOW = Instant.parse("2026-11-15T09:00:00Z");

	private static final String PROBE = "/api/admin/test/probe";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	AdminProbe probe;

	@BeforeEach
	void reset() {
		this.probe.calls.set(0);
	}

	@Test
	void adminSessionOpensTheAdminPaths() throws Exception {
		Cookie session = sessionCookie(signIn(account("admin@example.com", AccountRole.ADMIN, false)));

		this.mockMvc.perform(get(PROBE).cookie(session)).andExpect(status().isOk()).andExpect(content().string("admin"));
	}

	@Test
	void changingRequestsOfASessionNeedTheCsrfToken() throws Exception {
		Cookie session = sessionCookie(signIn(account("csrf@example.com", AccountRole.ADMIN, false)));

		this.mockMvc.perform(post(PROBE).cookie(session))
			.andExpect(status().isForbidden())
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:csrf-rejected","title":"요청 보호 토큰이 맞지 않는다","status":403,
					 "detail":"요청 보호 토큰(X-XSRF-TOKEN)이 없거나 맞지 않는다","instance":"/api/admin/test/probe"}
					""", JsonCompareMode.STRICT));
		Cookie csrf = csrfCookie();
		this.mockMvc.perform(post(PROBE).cookie(session).cookie(csrf).header("X-XSRF-TOKEN", "other-value"))
			.andExpect(status().isForbidden());
		assertThat(this.probe.calls).hasValue(0);
		this.mockMvc.perform(withCsrf(post(PROBE).cookie(session), csrf)).andExpect(status().isOk());
		assertThat(this.probe.calls).hasValue(1);
	}

	@Test
	void anAdminWhoMustChangeThePasswordIsStopped() throws Exception {
		Cookie session = sessionCookie(signIn(account("initial@example.com", AccountRole.ADMIN, true)));

		this.mockMvc.perform(get(PROBE).cookie(session))
			.andExpect(status().isForbidden())
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:password-change-required","title":"비밀번호를 바꿔야 한다","status":403,
					 "detail":"비밀번호를 바꾸기 전에는 비밀번호 변경 · 로그인 상태 조회 · 로그아웃만 쓴다",
					 "instance":"/api/admin/test/probe"}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void aUserSessionIsForbidden() throws Exception {
		long user = account("user@example.com", AccountRole.USER, false);
		LlmKeyRows.give(this.jdbc, user);
		Cookie session = sessionCookie(signIn(user));

		this.mockMvc.perform(get(PROBE).cookie(session))
			.andExpect(status().isForbidden())
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:admin-forbidden","title":"운영자 권한이 없다","status":403,
					 "detail":"운영자 권한이 없다","instance":"/api/admin/test/probe"}
					""", JsonCompareMode.STRICT));
		assertThat(this.probe.calls).hasValue(0);
	}

	@Test
	void anAdminWithoutALlmKeyStillOpensTheAdminPaths() throws Exception {
		Cookie session = sessionCookie(signIn(account("keyless@example.com", AccountRole.ADMIN, false)));

		this.mockMvc.perform(get(PROBE).cookie(session)).andExpect(status().isOk());
		// 대조 — 같은 세션이 세션 체인의 키 필요 처리기에서는 막힌다
		this.mockMvc.perform(get("/api/test/session/me").cookie(session))
			.andExpect(status().isForbidden())
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:llm-key-required"}"""));
	}

	@Test
	void anonymousRequestsNeedASignIn() throws Exception {
		this.mockMvc.perform(get(PROBE))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:unauthenticated","title":"로그인이 필요하다","status":401,
					 "detail":"로그인하지 않았거나 세션이 만료됐다","instance":"/api/admin/test/probe"}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void aWrongBearerHeaderNeverFallsBackToTheSession() throws Exception {
		Cookie session = sessionCookie(signIn(account("fallback@example.com", AccountRole.ADMIN, false)));

		this.mockMvc
			.perform(post(PROBE).cookie(session).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN + "0"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:admin-unauthorized","title":"운영자 인증이 필요하다","status":401,
					 "detail":"운영자 토큰(Bearer)이 맞지 않거나 형식이 틀리다","instance":"/api/admin/test/probe"}
					""", JsonCompareMode.STRICT));
		assertThat(this.probe.calls).hasValue(0);
		// 읽기 요청은 CSRF 가 없어도 세션으로 열리므로, 세션으로 넘어갔다면 200 이다
		this.mockMvc.perform(get(PROBE).cookie(session).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN + "0"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:admin-unauthorized"}"""));
	}

	@Test
	void theRightTokenChangesWithoutTheCsrfToken() throws Exception {
		this.mockMvc.perform(post(PROBE).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
			.andExpect(status().isOk());
		assertThat(this.probe.calls).hasValue(1);
	}

	@Test
	void theRightTokenIsJudgedWhateverTheSession() throws Exception {
		long user = account("suspended@example.com", AccountRole.ADMIN, false);
		Cookie session = sessionCookie(signIn(user));
		this.jdbc.sql("update users set status = 'SUSPENDED' where id = :id").param("id", user).update();

		this.mockMvc.perform(get(PROBE).cookie(session).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
			.andExpect(status().isOk());
	}

	@Test
	void theRightTokenLeavesNoSession() throws Exception {
		int before = allSessionRows();

		MockHttpServletResponse read = this.mockMvc
			.perform(get(PROBE).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();
		MockHttpServletResponse changed = this.mockMvc
			.perform(post(PROBE).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();

		assertThat(setCookieOrNull(read, "SESSION")).as("읽기 응답의 세션 쿠키").isNull();
		assertThat(setCookieOrNull(changed, "SESSION")).as("변경 응답의 세션 쿠키").isNull();
		assertThat(allSessionRows()).as("세션 행 수").isEqualTo(before);
		// 응답이 준 쿠키(CSRF 토큰 쿠키뿐)만으로는 운영자가 아니다
		MockHttpServletRequestBuilder again = get(PROBE);
		for (MockHttpServletResponse response : List.of(read, changed)) {
			for (Cookie cookie : response.getCookies()) {
				again.cookie(cookie);
			}
		}
		this.mockMvc.perform(again).andExpect(status().isUnauthorized());
	}

	@Test
	void requestsWithoutAHandlerAreJudgedFirst() throws Exception {
		this.mockMvc.perform(get("/api/admin/test/none"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:unauthenticated"}"""));
		Cookie session = sessionCookie(signIn(account("nowhere@example.com", AccountRole.ADMIN, false)));
		this.mockMvc.perform(get("/api/admin/test/none").cookie(session)).andExpect(status().isNotFound());
	}

	private long account(String email, AccountRole role, boolean mustChange) {
		long user = this.accounts.save(Account.create(EmailAddress.parse(email).orElseThrow(),
				new DisplayName("시험"), role, NOW))
			.id()
			.value();
		this.credentials.save(PasswordCredential.of(new UserId(user), "$argon2id$hash", mustChange, NOW));
		return user;
	}

	private Cookie csrfCookie() throws Exception {
		MockHttpServletResponse response = this.mockMvc.perform(get("/api/test/session/open")).andReturn().getResponse();
		String header = setCookieOrNull(response, "XSRF-TOKEN");
		assertThat(header).as("Set-Cookie XSRF-TOKEN").isNotNull();
		return new Cookie("XSRF-TOKEN", cookieValue(header));
	}

	private MockHttpServletResponse signIn(long user) throws Exception {
		return this.mockMvc
			.perform(withCsrf(post("/api/test/session/sign-in").param("user", Long.toString(user))
				.param("keep", "false"), csrfCookie()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();
	}

	private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, Cookie csrf) {
		return request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
	}

	private static Cookie sessionCookie(MockHttpServletResponse response) {
		String header = setCookieOrNull(response, "SESSION");
		assertThat(header).as("Set-Cookie SESSION").isNotNull();
		return new Cookie("SESSION", cookieValue(header));
	}

	private int allSessionRows() {
		return this.jdbc.sql("select count(*) from spring_session").query(Integer.class).single();
	}

	private static @Nullable String setCookieOrNull(MockHttpServletResponse response, String name) {
		return response.getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(header -> header.startsWith(name + "="))
			.reduce((a, b) -> b)
			.orElse(null);
	}

	private static String cookieValue(String setCookie) {
		String pair = setCookie.split(";", 2)[0];
		return pair.substring(pair.indexOf('=') + 1);
	}

	// 중첩 클래스라 다른 시험 컨텍스트의 컴포넌트 스캔에는 걸리지 않는다 — 이 컨텍스트에만 명시해 등록한다
	@RestController
	static class AdminProbe {

		final AtomicInteger calls = new AtomicInteger();

		@GetMapping(PROBE)
		String read() {
			return "admin";
		}

		@PostMapping(PROBE)
		String change() {
			this.calls.incrementAndGet();
			return "changed";
		}

	}

}
