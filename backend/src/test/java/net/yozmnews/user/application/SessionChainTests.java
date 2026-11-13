package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.YozmnewsApplication;
import net.yozmnews.platform.AllowedBeforePasswordChange;
import net.yozmnews.platform.PublicApi;
import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.credential.PasswordCredential;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세션 체인 — 실제 PostgreSQL 의 Spring Session 위에서 공개 처리기 · 세션 사용자 요구, CSRF, 세션 쿠키 속성과 수명, 세션 ID 교체, 요청마다 계정
 * 확인(비밀번호 강제 변경 포함), 로그아웃, 만료 세션 정리를 본다. 세션은 시험 처리기가 {@link SessionSignIn} 을 직접 불러 만든다.
 * <p>
 * 지키는 규칙: user-R3 · user-R5 · user-R6 · user-R7 · user-R8 · user-R9 · user-R10 · user-R11 · user-R12
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(classes = { YozmnewsApplication.class, SessionChainTests.SessionProbe.class })
@AutoConfigureMockMvc
class SessionChainTests {

	private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	SessionExpiry expiry;

	@Autowired
	JdbcClient jdbc;

	@Test
	void protectedHandlerNeedsASessionUser() throws Exception {
		this.mockMvc.perform(get("/api/test/session/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:unauthenticated","title":"로그인이 필요하다","status":401,
					 "detail":"로그인하지 않았거나 세션이 만료됐다","instance":"/api/test/session/me"}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void publicHandlerOpensWithoutASessionAndHandsOutTheCsrfCookie() throws Exception {
		MockHttpServletResponse response = this.mockMvc.perform(get("/api/test/session/open"))
			.andExpect(status().isOk())
			.andExpect(content().string("open"))
			.andReturn()
			.getResponse();

		// CSRF 저장소는 쿠키 객체로 쓴다 — 시험 응답은 그 SameSite 를 헤더 문자열에 그리지 않아 쿠키 객체로 본다
		Cookie csrf = response.getCookie("XSRF-TOKEN");
		assertThat(csrf).isNotNull();
		assertThat(csrf.getValue()).isNotEmpty();
		assertThat(csrf.getPath()).isEqualTo("/");
		assertThat(csrf.getSecure()).isTrue();
		assertThat(csrf.isHttpOnly()).isFalse();
		assertThat(csrf.getAttribute("SameSite")).isEqualTo("Lax");
		assertThat(setCookieOrNull(response, "SESSION")).as("익명 요청은 세션을 만들지 않는다").isNull();
	}

	@Test
	void changingRequestWithoutTheCsrfHeaderIsRejected() throws Exception {
		Cookie csrf = csrfCookie();

		this.mockMvc.perform(post("/api/test/session/sign-out").cookie(csrf))
			.andExpect(status().isForbidden())
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:csrf-rejected","title":"요청 보호 토큰이 맞지 않는다","status":403,
					 "detail":"요청 보호 토큰(X-XSRF-TOKEN)이 없거나 맞지 않는다","instance":"/api/test/session/sign-out"}
					""", JsonCompareMode.STRICT));
		this.mockMvc.perform(post("/api/test/session/sign-out").cookie(csrf).header("X-XSRF-TOKEN", "other-value"))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(withCsrf(post("/api/test/session/sign-out"), csrf)).andExpect(status().isOk());
	}

	@Test
	void signInWritesASecureSessionCookieAndKeepDecidesItsLifetime() throws Exception {
		long user = account("keep@example.com", AccountRole.USER);

		MockHttpServletResponse kept = signIn(user, true, null);
		String keptCookie = setCookie(kept, "SESSION");
		assertThat(keptCookie).contains("; Path=/")
			.contains("; Secure")
			.contains("; HttpOnly")
			.contains("; SameSite=Lax")
			.contains("; Max-Age=2147483647");
		assertThat(maxInactiveInterval(keptCookie)).isEqualTo(604_800);
		assertThat(principalName(keptCookie)).isEqualTo(Long.toString(user));

		MockHttpServletResponse brief = signIn(user, false, null);
		String briefCookie = setCookie(brief, "SESSION");
		assertThat(briefCookie).contains("; Secure").contains("; HttpOnly").doesNotContain("Max-Age");
		assertThat(maxInactiveInterval(briefCookie)).isEqualTo(7_200);
	}

	@Test
	void signInReplacesTheSessionIdAndTheCsrfToken() throws Exception {
		long user = account("fixation@example.com", AccountRole.USER);
		Cookie csrf = csrfCookie();
		Cookie first = sessionCookie(signIn(user, false, null));

		MockHttpServletResponse again = this.mockMvc
			.perform(withCsrf(post("/api/test/session/sign-in").param("user", Long.toString(user))
				.param("keep", "false")
				.cookie(first), csrf))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();
		Cookie second = sessionCookie(again);

		assertThat(second.getValue()).isNotEqualTo(first.getValue());
		assertThat(sessionRows(first)).as("옛 세션 ID 는 남지 않는다").isZero();
		assertThat(sessionRows(second)).isOne();
		String rotated = cookieValue(setCookie(again, "XSRF-TOKEN"));
		assertThat(rotated).isNotEmpty().isNotEqualTo(csrf.getValue());
	}

	@Test
	void sessionUserReachesTheHandlerWithTheRoleReadOnEachRequest() throws Exception {
		long user = account("role@example.com", AccountRole.USER);
		Cookie session = sessionCookie(signIn(user, false, null));

		this.mockMvc.perform(get("/api/test/session/me").cookie(session))
			.andExpect(status().isOk())
			.andExpect(content().string(user + ":ROLE_USER"));

		this.jdbc.sql("update users set role = 'ADMIN' where id = :id").param("id", user).update();
		this.mockMvc.perform(get("/api/test/session/me").cookie(session))
			.andExpect(status().isOk())
			.andExpect(content().string(user + ":ROLE_ADMIN"));
	}

	@Test
	void sessionUserWhoMustChangeThePasswordReachesOnlyPublicAndAllowedHandlers() throws Exception {
		long user = account("initial@example.com", AccountRole.ADMIN);
		credential(user, true);
		Cookie session = sessionCookie(signIn(user, false, null));

		this.mockMvc.perform(get("/api/test/session/me").cookie(session))
			.andExpect(status().isForbidden())
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:password-change-required","title":"비밀번호를 바꿔야 한다","status":403,
					 "detail":"비밀번호를 바꾸기 전에는 비밀번호 변경 · 로그인 상태 조회 · 로그아웃만 쓴다",
					 "instance":"/api/test/session/me"}
					""", JsonCompareMode.STRICT));
		this.mockMvc.perform(get("/api/notices").cookie(session))
			.andExpect(status().isForbidden())
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:password-change-required"}"""));
		// 허용 표시가 있는 처리기는 열리고, 그 요청의 권한에는 역할이 없다
		this.mockMvc.perform(get("/api/test/session/before-password-change").cookie(session))
			.andExpect(status().isOk())
			.andExpect(content().string(user + ":PASSWORD_CHANGE_REQUIRED"));
		this.mockMvc.perform(get("/api/test/session/open").cookie(session)).andExpect(status().isOk());
		assertThat(sessionRows(session)).as("거부해도 세션은 남는다").isOne();
	}

	@Test
	void passwordChangeIsReadOnEachRequest() throws Exception {
		long user = account("changed@example.com", AccountRole.USER);
		credential(user, true);
		Cookie session = sessionCookie(signIn(user, false, null));
		this.mockMvc.perform(get("/api/test/session/me").cookie(session)).andExpect(status().isForbidden());

		this.jdbc.sql("update user_password_credentials set must_change = false where user_id = :id")
			.param("id", user)
			.update();

		this.mockMvc.perform(get("/api/test/session/me").cookie(session))
			.andExpect(status().isOk())
			.andExpect(content().string(user + ":ROLE_USER"));
		this.mockMvc.perform(get("/api/notices").cookie(session)).andExpect(status().isOk());
	}

	@Test
	void inactiveAccountLosesItsSessionOnTheNextRequest() throws Exception {
		long user = account("suspended@example.com", AccountRole.USER);
		Cookie session = sessionCookie(signIn(user, false, null));
		this.jdbc.sql("update users set status = 'SUSPENDED' where id = :id").param("id", user).update();

		this.mockMvc.perform(get("/api/test/session/me").cookie(session)).andExpect(status().isUnauthorized());
		assertThat(sessionRows(session)).isZero();
	}

	@Test
	void unreadableSessionIsDiscardedAndTheUserCanSignInAgain() throws Exception {
		long user = account("unreadable@example.com", AccountRole.USER);
		Cookie session = sessionCookie(signIn(user, false, null));
		corruptSecurityContext(session);

		this.mockMvc.perform(get("/api/test/session/me").cookie(session))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:unauthenticated","title":"로그인이 필요하다","status":401,
					 "detail":"로그인하지 않았거나 세션이 만료됐다","instance":"/api/test/session/me"}
					""", JsonCompareMode.STRICT));
		assertThat(sessionRows(session)).isZero();
		this.mockMvc.perform(get("/api/test/session/open").cookie(session)).andExpect(status().isOk());

		// 읽지 못하는 세션의 쿠키를 단 채 보낸 첫 요청이 로그인이어도 된다 — 옛 세션은 지워지고 새 세션이 생긴다
		Cookie stale = sessionCookie(signIn(user, false, null));
		corruptSecurityContext(stale);
		MockHttpServletResponse again = this.mockMvc
			.perform(withCsrf(post("/api/test/session/sign-in").param("user", Long.toString(user))
				.param("keep", "false")
				.cookie(stale), csrfCookie()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();
		assertThat(sessionRows(stale)).isZero();
		assertThat(sessionRows(sessionCookie(again))).isOne();
	}

	/** 배포로 직렬화 형식이 바뀐 세션 — 보안 문맥 속성의 바이트를 자바 직렬화로 읽을 수 없는 값으로 바꾼다 */
	private void corruptSecurityContext(Cookie session) {
		int corrupted = this.jdbc.sql("""
				update spring_session_attributes set attribute_bytes = '\\x01020304'::bytea
				where attribute_name = 'SPRING_SECURITY_CONTEXT'
				  and session_primary_id = (select primary_id from spring_session where session_id = :id)
				""").param("id", sessionId(session.getValue())).update();
		assertThat(corrupted).isOne();
	}

	@Test
	void signOutRemovesTheSessionAndExpiresBothCookies() throws Exception {
		long user = account("bye@example.com", AccountRole.USER);
		Cookie csrf = csrfCookie();
		MockHttpServletResponse signedIn = signIn(user, true, csrf);
		Cookie session = sessionCookie(signedIn);
		Cookie rotated = new Cookie("XSRF-TOKEN", cookieValue(setCookie(signedIn, "XSRF-TOKEN")));

		MockHttpServletResponse out = this.mockMvc
			.perform(withCsrf(post("/api/test/session/sign-out").cookie(session), rotated))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();

		assertThat(setCookie(out, "SESSION")).contains("Max-Age=0");
		assertThat(setCookie(out, "XSRF-TOKEN")).contains("Max-Age=0");
		assertThat(sessionRows(session)).isZero();
		this.mockMvc.perform(get("/api/test/session/me").cookie(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void requestsWithoutAHandlerAreLeftToMvc() throws Exception {
		// 변경 요청은 처리기가 없어도 CSRF 를 먼저 본다 — 토큰을 실으면 MVC 가 405 를 낸다
		this.mockMvc.perform(put("/api/test/session/open")).andExpect(status().isForbidden());
		this.mockMvc.perform(withCsrf(put("/api/test/session/open"), csrfCookie()))
			.andExpect(status().isMethodNotAllowed());
		this.mockMvc.perform(get("/api/test/session/nowhere")).andExpect(status().isNotFound());
	}

	@Test
	void expiryJobRemovesOnlyExpiredSessions() {
		long now = System.currentTimeMillis();
		// 세션 ID 칸은 고정 길이 36 자다
		insertSession("expired-session-primary-key-00000001", "expired-session-id-00000000000000001", now - 1_000);
		insertSession("living-session-primary-key-000000001", "living-session-id-000000000000000001", now + 600_000);

		this.expiry.cleanUp();

		assertThat(this.jdbc.sql("select session_id from spring_session where session_id like '%-session-id-%'")
			.query(String.class)
			.list()).containsExactly("living-session-id-000000000000000001");
	}

	private long account(String email, AccountRole role) {
		return this.accounts.save(Account.create(EmailAddress.parse(email).orElseThrow(),
				new DisplayName("시험"), role, NOW))
			.id()
			.value();
	}

	private void credential(long user, boolean mustChange) {
		this.credentials.save(PasswordCredential.of(new UserId(user), "$argon2id$hash", mustChange, NOW));
	}

	private Cookie csrfCookie() throws Exception {
		MockHttpServletResponse response = this.mockMvc.perform(get("/api/test/session/open")).andReturn().getResponse();
		return new Cookie("XSRF-TOKEN", cookieValue(setCookie(response, "XSRF-TOKEN")));
	}

	private MockHttpServletResponse signIn(long user, boolean keep, @Nullable Cookie csrf) throws Exception {
		return this.mockMvc
			.perform(withCsrf(post("/api/test/session/sign-in").param("user", Long.toString(user))
				.param("keep", Boolean.toString(keep)), (csrf != null) ? csrf : csrfCookie()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();
	}

	private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, Cookie csrf) {
		return request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
	}

	private static Cookie sessionCookie(MockHttpServletResponse response) {
		return new Cookie("SESSION", cookieValue(setCookie(response, "SESSION")));
	}

	private int maxInactiveInterval(String sessionSetCookie) {
		return this.jdbc.sql("select max_inactive_interval from spring_session where session_id = :id")
			.param("id", sessionId(cookieValue(sessionSetCookie)))
			.query(Integer.class)
			.single();
	}

	private String principalName(String sessionSetCookie) {
		return this.jdbc.sql("select principal_name from spring_session where session_id = :id")
			.param("id", sessionId(cookieValue(sessionSetCookie)))
			.query(String.class)
			.single();
	}

	private int sessionRows(Cookie session) {
		return this.jdbc.sql("select count(*) from spring_session where session_id = :id")
			.param("id", sessionId(session.getValue()))
			.query(Integer.class)
			.single();
	}

	private void insertSession(String primaryId, String sessionId, long expiryTime) {
		this.jdbc.sql("""
				insert into spring_session
				  (primary_id, session_id, creation_time, last_access_time, max_inactive_interval, expiry_time)
				values (:primary, :session, 0, 0, 60, :expiry)
				""").param("primary", primaryId).param("session", sessionId).param("expiry", expiryTime).update();
	}

	/** 세션 쿠키 값은 세션 ID 의 Base64 다 */
	private static String sessionId(String cookieValue) {
		return new String(Base64.getDecoder().decode(cookieValue), StandardCharsets.UTF_8);
	}

	private static String setCookie(MockHttpServletResponse response, String name) {
		String header = setCookieOrNull(response, name);
		assertThat(header).as("Set-Cookie " + name).isNotNull();
		return header;
	}

	private static @Nullable String setCookieOrNull(MockHttpServletResponse response, String name) {
		List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
		return headers.stream().filter(header -> header.startsWith(name + "=")).reduce((a, b) -> b).orElse(null);
	}

	private static String cookieValue(String setCookie) {
		String pair = setCookie.split(";", 2)[0];
		return pair.substring(pair.indexOf('=') + 1);
	}

	// 중첩 클래스라 다른 시험 컨텍스트의 컴포넌트 스캔에는 걸리지 않는다 — 이 컨텍스트에만 명시해 등록한다
	@RestController
	static class SessionProbe {

		private final SessionSignIn sessions;

		SessionProbe(SessionSignIn sessions) {
			this.sessions = sessions;
		}

		@PublicApi(reason = "시험 — 세션을 만든다")
		@PostMapping("/api/test/session/sign-in")
		void signIn(@RequestParam long user, @RequestParam boolean keep, HttpServletRequest request,
				HttpServletResponse response) {
			this.sessions.signIn(new UserId(user), keep, request, response);
		}

		@PublicApi(reason = "시험 — 세션을 지운다")
		@PostMapping("/api/test/session/sign-out")
		void signOut(HttpServletRequest request, HttpServletResponse response) {
			this.sessions.signOut(request, response);
		}

		@PublicApi(reason = "시험 — 공개 조회")
		@GetMapping("/api/test/session/open")
		String open() {
			return "open";
		}

		@AllowedBeforePasswordChange(reason = "시험 — 비밀번호를 바꾸기 전에도 연다")
		@GetMapping("/api/test/session/before-password-change")
		String beforePasswordChange(UserId user) {
			return me(user);
		}

		@GetMapping("/api/test/session/me")
		String me(UserId user) {
			String roles = SecurityContextHolder.getContext()
				.getAuthentication()
				.getAuthorities()
				.stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.joining(","));
			return user.value() + ":" + roles;
		}

	}

}
