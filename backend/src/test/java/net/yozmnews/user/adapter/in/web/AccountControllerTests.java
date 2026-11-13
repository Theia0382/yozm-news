package net.yozmnews.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.Cookie;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.user.UserId;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.credential.PasswordCredential;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.domain.recovery.RecoveryCodeRepository;
import net.yozmnews.user.domain.secret.RecoveryCode;
import net.yozmnews.user.domain.secret.SecretDigest;
import net.yozmnews.user.domain.throttle.EmailDigest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * 비밀번호 변경 · 복구 코드 재발급 · 복구 코드 재설정 — 실제 PostgreSQL 의 세션 위에서 판정 결과, 세션 유지 · 종료, 복구 코드의 교체, 비밀번호
 * 알림, 실패 기록의 종류를 본다.
 * <p>
 * 지키는 규칙: user-R11 · user-R17 · user-R19 · user-R21 · user-R29 · user-R30
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTests {

	private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

	private static final String PASSWORD = "correct horse battery";

	private static final String NEW_PASSWORD = "brand new horse staple";

	private static final String CODE = "0123-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ";

	private static final String CODE_PATTERN = "^[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){7}$";

	private static final String CSRF = "account-controller-tests";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	RecoveryCodeRepository recoveryCodes;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from users").update();
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from spring_session").update();
	}

	@Test
	void changingThePasswordKeepsThisSessionUnderANewIdEndsTheOthersAndLeavesANotice() throws Exception {
		long user = account("alice@example.com", false);
		Session here = signIn("alice@example.com", PASSWORD);
		Session elsewhere = signIn("alice@example.com", PASSWORD);

		MockHttpServletResponse response = this.mockMvc
			.perform(with(put("/api/account/password"), here).contentType(MediaType.APPLICATION_JSON).content("""
					{"currentPassword":"%s","newPassword":"%s"}""".formatted(PASSWORD, NEW_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"CHANGED","recoveryCode":null,"retryAfterSeconds":null,"passwordProblem":null}
					""", JsonCompareMode.STRICT))
			.andReturn()
			.getResponse();

		// 이 세션은 새 ID 와 새 CSRF 토큰으로 이어진다
		Session renewed = new Session(cookie(response, "SESSION"), cookie(response, "XSRF-TOKEN"));
		assertThat(renewed.session().getValue()).isNotEqualTo(here.session().getValue());
		assertThat(renewed.csrf()).isNotEqualTo(here.csrf());
		this.mockMvc.perform(get("/api/me").cookie(renewed.session()))
			.andExpect(jsonPath("$.result").value("SIGNED_IN"));
		this.mockMvc.perform(with(post("/api/account/recovery-code"), renewed).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"%s"}""".formatted(NEW_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("ISSUED"));
		// 옛 ID 와 다른 세션은 끝났다
		this.mockMvc.perform(get("/api/me").cookie(here.session())).andExpect(jsonPath("$.result").value("ANONYMOUS"));
		this.mockMvc.perform(get("/api/me").cookie(elsewhere.session()))
			.andExpect(jsonPath("$.result").value("ANONYMOUS"));
		assertThat(notices(user)).containsExactly("PASSWORD_CHANGED:CHANGE");
		assertThat(signInResult("alice@example.com", NEW_PASSWORD)).isEqualTo("SIGNED_IN");
		assertThat(signInResult("alice@example.com", PASSWORD)).isEqualTo("REJECTED");
	}

	@Test
	void wrongCurrentPasswordIsRejectedAndCountsAsASignInFailure() throws Exception {
		long user = account("bob@example.com", false);
		Session session = signIn("bob@example.com", PASSWORD);

		this.mockMvc.perform(with(put("/api/account/password"), session).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"wrong horse battery","newPassword":"%s"}""".formatted(NEW_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"REJECTED","recoveryCode":null,"retryAfterSeconds":null,"passwordProblem":null}
					""", JsonCompareMode.STRICT));

		assertThat(failures("SIGN_IN", "bob@example.com")).isOne();
		assertThat(failures("RECOVERY", "bob@example.com")).isZero();
		assertThat(notices(user)).isEmpty();
		assertThat(signInResult("bob@example.com", PASSWORD)).isEqualTo("SIGNED_IN");
	}

	@Test
	void manySignInFailuresMakeThePasswordChangeWaitWithoutComparing() throws Exception {
		account("carol@example.com", false);
		Session session = signIn("carol@example.com", PASSWORD);
		// 연속 실패 10회 — 대기는 1초 × 2^6 = 64초다
		failed("SIGN_IN", "carol@example.com", 10);

		this.mockMvc.perform(with(put("/api/account/password"), session).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"%s","newPassword":"%s"}""".formatted(PASSWORD, NEW_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("WAIT"))
			.andExpect(jsonPath("$.retryAfterSeconds").value(org.hamcrest.Matchers.allOf(
					org.hamcrest.Matchers.greaterThanOrEqualTo(60), org.hamcrest.Matchers.lessThanOrEqualTo(64))));
		assertThat(failures("SIGN_IN", "carol@example.com")).as("기다리는 동안은 세지 않는다").isEqualTo(10);
	}

	@Test
	void newPasswordBreakingTheRuleIsRejectedBeforeTheCurrentOneIsChecked() throws Exception {
		account("dave@example.com", false);
		Session session = signIn("dave@example.com", PASSWORD);

		this.mockMvc.perform(with(put("/api/account/password"), session).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"wrong horse battery","newPassword":"short"}"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"PASSWORD_REJECTED","recoveryCode":null,"retryAfterSeconds":null,"passwordProblem":"TOO_SHORT"}
					""", JsonCompareMode.STRICT));
		assertThat(failures("SIGN_IN", "dave@example.com")).isZero();
	}

	@Test
	void forcedChangeIsAllowedGivesARecoveryCodeLeavesNoNoticeAndOpensTheOtherHandlers() throws Exception {
		long user = account("erin@example.com", true);
		Session session = signIn("erin@example.com", PASSWORD);
		this.mockMvc.perform(get("/api/notices").cookie(session.session())).andExpect(status().isForbidden());

		MockHttpServletResponse response = this.mockMvc
			.perform(with(put("/api/account/password"), session).contentType(MediaType.APPLICATION_JSON).content("""
					{"currentPassword":"%s","newPassword":"%s"}""".formatted(PASSWORD, NEW_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("CHANGED"))
			.andExpect(jsonPath("$.recoveryCode").value(org.hamcrest.Matchers.matchesPattern(CODE_PATTERN)))
			.andReturn()
			.getResponse();

		String code = com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.recoveryCode");
		assertThat(storedCode(user).matches(RecoveryCode.parse(code).orElseThrow().digest())).isTrue();
		assertThat(mustChange(user)).isFalse();
		assertThat(notices(user)).as("첫 로그인의 강제 변경은 알리지 않는다").isEmpty();
		this.mockMvc.perform(get("/api/notices").cookie(cookie(response, "SESSION")))
			.andExpect(status().isOk());
	}

	@Test
	void reissuingGivesANewCodeAndTheOldOneStopsWorking() throws Exception {
		long user = account("frank@example.com", false);
		storeCode(user, CODE);
		Session session = signIn("frank@example.com", PASSWORD);

		MockHttpServletResponse response = this.mockMvc
			.perform(with(post("/api/account/recovery-code"), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"currentPassword":"%s"}""".formatted(PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("ISSUED"))
			.andExpect(jsonPath("$.recoveryCode").value(org.hamcrest.Matchers.matchesPattern(CODE_PATTERN)))
			.andExpect(jsonPath("$.retryAfterSeconds").isEmpty())
			.andReturn()
			.getResponse();
		String issued = com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.recoveryCode");

		assertThat(recover("frank@example.com", CODE, NEW_PASSWORD).result()).isEqualTo("REJECTED");
		assertThat(recover("frank@example.com", issued, NEW_PASSWORD).result()).isEqualTo("RESET");
	}

	@Test
	void reissuingWithAWrongPasswordIsRejectedAndCountsAsASignInFailure() throws Exception {
		long user = account("gina@example.com", false);
		storeCode(user, CODE);
		Session session = signIn("gina@example.com", PASSWORD);

		this.mockMvc.perform(with(post("/api/account/recovery-code"), session).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"wrong horse battery"}"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"REJECTED","recoveryCode":null,"retryAfterSeconds":null}
					""", JsonCompareMode.STRICT));
		assertThat(failures("SIGN_IN", "gina@example.com")).isOne();
		assertThat(storedCode(user).matches(RecoveryCode.parse(CODE).orElseThrow().digest())).isTrue();
	}

	@Test
	void recoveryResetsThePasswordSignsInEndsEverySessionAndReplacesTheCode() throws Exception {
		long user = account("hana@example.com", true);
		storeCode(user, CODE);
		Session before = signIn("hana@example.com", PASSWORD);

		Recovered recovered = recover("hana@example.com", CODE.toLowerCase().replace("-", " "), NEW_PASSWORD);

		assertThat(recovered.result()).isEqualTo("RESET");
		assertThat(recovered.recoveryCode()).matches(CODE_PATTERN).isNotEqualTo(CODE);
		this.mockMvc.perform(get("/api/me").cookie(recovered.session()))
			.andExpect(jsonPath("$.result").value("SIGNED_IN"))
			.andExpect(jsonPath("$.passwordChangeRequired").value(false));
		this.mockMvc.perform(get("/api/me").cookie(before.session())).andExpect(jsonPath("$.result").value("ANONYMOUS"));
		assertThat(sessions(user)).isOne();
		assertThat(notices(user)).containsExactly("PASSWORD_CHANGED:RECOVERY");
		assertThat(recover("hana@example.com", CODE, "another horse staple").result()).isEqualTo("REJECTED");
		assertThat(signInResult("hana@example.com", NEW_PASSWORD)).isEqualTo("SIGNED_IN");
	}

	@Test
	void recoveryFromABrowserStillSignedInAsThatAccountSignsInAfresh() throws Exception {
		long user = account("olga@example.com", false);
		storeCode(user, CODE);
		Session stale = signIn("olga@example.com", PASSWORD);

		MockHttpServletResponse response = this.mockMvc
			.perform(with(post("/api/account-recoveries"), stale).contentType(MediaType.APPLICATION_JSON).content("""
					{"email":"olga@example.com","recoveryCode":"%s","newPassword":"%s","keep":false}""".formatted(CODE,
					NEW_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("RESET"))
			.andReturn()
			.getResponse();

		this.mockMvc.perform(get("/api/me").cookie(cookie(response, "SESSION")))
			.andExpect(jsonPath("$.result").value("SIGNED_IN"));
		this.mockMvc.perform(get("/api/me").cookie(stale.session())).andExpect(jsonPath("$.result").value("ANONYMOUS"));
		assertThat(sessions(user)).isOne();
	}

	@Test
	void recoveryRejectionsLookTheSameWhateverWentWrong() throws Exception {
		long user = account("ian@example.com", false);
		storeCode(user, CODE);
		long suspended = account("judy@example.com", false);
		storeCode(suspended, CODE);
		this.jdbc.sql("update users set status = 'SUSPENDED' where id = :id").param("id", suspended).update();

		String wrongCode = recoverBody("ian@example.com", "ZZZZ-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ");
		String unreadable = recoverBody("ian@example.com", "not a code");
		String noAccount = recoverBody("nobody@example.com", CODE);
		String inactive = recoverBody("judy@example.com", CODE);
		long withoutCode = account("kim@example.com", false);
		String noCode = recoverBody("kim@example.com", CODE);

		assertThat(wrongCode).isEqualTo("""
				{"result":"REJECTED","recoveryCode":null,"retryAfterSeconds":null,"passwordProblem":null}""");
		assertThat(List.of(unreadable, noAccount, inactive, noCode)).containsOnly(wrongCode);
		assertThat(failures("RECOVERY", "nobody@example.com")).as("없는 이메일도 실패를 센다").isOne();
		assertThat(notices(withoutCode)).isEmpty();
	}

	@Test
	void recoveryFailuresAreCountedApartFromSignInFailures() throws Exception {
		long user = account("lee@example.com", false);
		storeCode(user, CODE);
		// 로그인 연속 실패가 많아도 복구는 기다리지 않는다
		failed("SIGN_IN", "lee@example.com", 10);
		assertThat(recover("lee@example.com", CODE, NEW_PASSWORD).result()).isEqualTo("RESET");
		assertThat(failures("SIGN_IN", "lee@example.com")).as("복구는 로그인 실패 기록을 건드리지 않는다").isEqualTo(10);

		// 복구 연속 실패는 복구를 기다리게 한다
		long other = account("min@example.com", false);
		storeCode(other, CODE);
		failed("RECOVERY", "min@example.com", 10);
		Recovered waiting = recover("min@example.com", CODE, NEW_PASSWORD);
		assertThat(waiting.result()).isEqualTo("WAIT");
		assertThat(waiting.retryAfterSeconds()).isBetween(60L, 64L);
	}

	@Test
	void fourRecoveryFailuresMakeEvenTheRightCodeWaitASecond() throws Exception {
		long user = account("ola@example.com", false);
		storeCode(user, CODE);
		for (int i = 0; i < 4; i++) {
			assertThat(recover("ola@example.com", "ZZZZ-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ", NEW_PASSWORD).result())
				.isEqualTo("REJECTED");
		}

		Recovered waiting = recover("ola@example.com", CODE, NEW_PASSWORD);

		assertThat(waiting.result()).isEqualTo("WAIT");
		assertThat(waiting.retryAfterSeconds()).isEqualTo(1L);
	}

	@Test
	void recoveryWithANewPasswordBreakingTheRuleChecksNothing() throws Exception {
		long user = account("nina@example.com", false);
		storeCode(user, CODE);

		assertThat(recoverBody("nina@example.com", CODE, "short")).isEqualTo("""
				{"result":"PASSWORD_REJECTED","recoveryCode":null,"retryAfterSeconds":null,"passwordProblem":"TOO_SHORT"}""");
		assertThat(failures("RECOVERY", "nina@example.com")).isZero();
	}

	@Test
	void passwordChangeAndReissueNeedASessionAndTheCsrfToken() throws Exception {
		this.mockMvc.perform(put("/api/account/password").cookie(new Cookie("XSRF-TOKEN", CSRF))
			.header("X-XSRF-TOKEN", CSRF)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"a","newPassword":"b"}"""))
			.andExpect(status().isUnauthorized());
		this.mockMvc.perform(post("/api/account/recovery-code").cookie(new Cookie("XSRF-TOKEN", CSRF))
			.header("X-XSRF-TOKEN", CSRF)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"a"}"""))
			.andExpect(status().isUnauthorized());
		this.mockMvc.perform(post("/api/account-recoveries").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"a@example.com","recoveryCode":"%s","newPassword":"%s","keep":false}""".formatted(CODE,
				NEW_PASSWORD)))
			.andExpect(status().isForbidden());
	}

	private long account(String email, boolean mustChange) {
		UserId id = this.accounts.save(Account.create(EmailAddress.parse(email).orElseThrow(),
				new DisplayName("시험"), AccountRole.USER, NOW)).id();
		this.credentials.save(PasswordCredential.of(id, this.hashing.hash(PASSWORD), mustChange, NOW));
		return id.value();
	}

	private void storeCode(long user, String code) {
		this.recoveryCodes.replace(new UserId(user), RecoveryCode.parse(code).orElseThrow().digest(), NOW);
	}

	private SecretDigest storedCode(long user) {
		return this.recoveryCodes.find(new UserId(user)).orElseThrow();
	}

	private Session signIn(String email, String password) throws Exception {
		MockHttpServletResponse response = this.mockMvc
			.perform(post("/api/session").cookie(new Cookie("XSRF-TOKEN", CSRF))
				.header("X-XSRF-TOKEN", CSRF)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s","keep":false}""".formatted(email, password)))
			.andExpect(jsonPath("$.result").value("SIGNED_IN"))
			.andReturn()
			.getResponse();
		return new Session(cookie(response, "SESSION"), cookie(response, "XSRF-TOKEN"));
	}

	private String signInResult(String email, String password) throws Exception {
		String body = this.mockMvc
			.perform(post("/api/session").cookie(new Cookie("XSRF-TOKEN", CSRF))
				.header("X-XSRF-TOKEN", CSRF)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s","keep":false}""".formatted(email, password)))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(body, "$.result");
	}

	private Recovered recover(String email, String code, String newPassword) throws Exception {
		MockHttpServletResponse response = this.mockMvc.perform(recovery(email, code, newPassword))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();
		String body = response.getContentAsString();
		Number retry = com.jayway.jsonpath.JsonPath.read(body, "$.retryAfterSeconds");
		return new Recovered(com.jayway.jsonpath.JsonPath.read(body, "$.result"),
				com.jayway.jsonpath.JsonPath.read(body, "$.recoveryCode"), (retry != null) ? retry.longValue() : null,
				(cookieOrNull(response, "SESSION") != null) ? cookie(response, "SESSION") : null);
	}

	private String recoverBody(String email, String code) throws Exception {
		return recoverBody(email, code, NEW_PASSWORD);
	}

	private String recoverBody(String email, String code, String newPassword) throws Exception {
		return this.mockMvc.perform(recovery(email, code, newPassword))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private static MockHttpServletRequestBuilder recovery(String email, String code, String newPassword) {
		return post("/api/account-recoveries").cookie(new Cookie("XSRF-TOKEN", CSRF))
			.header("X-XSRF-TOKEN", CSRF)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email":"%s","recoveryCode":"%s","newPassword":"%s","keep":false}""".formatted(email, code,
					newPassword));
	}

	private static MockHttpServletRequestBuilder with(MockHttpServletRequestBuilder request, Session session) {
		return request.cookie(session.session(), new Cookie("XSRF-TOKEN", session.csrf()))
			.header("X-XSRF-TOKEN", session.csrf());
	}

	private void failed(String attempt, String email, int failures) {
		this.jdbc.sql("""
				insert into login_failures (attempt, email_digest, failures, last_failed_at)
				values (:attempt, :digest, :failures, now())
				""")
			.param("attempt", attempt)
			.param("digest", EmailDigest.of(email).value())
			.param("failures", failures)
			.update();
	}

	private int failures(String attempt, String email) {
		return this.jdbc.sql("""
				select coalesce(sum(failures), 0) from login_failures where attempt = :attempt and email_digest = :digest
				""")
			.param("attempt", attempt)
			.param("digest", EmailDigest.of(email).value())
			.query(Integer.class)
			.single();
	}

	private List<String> notices(long user) {
		return this.jdbc.sql("""
				select kind || ':' || coalesce(content ->> 'method', '') from user_notices where user_id = :id order by id
				""").param("id", user).query(String.class).list();
	}

	private boolean mustChange(long user) {
		return this.credentials.findById(user).orElseThrow().mustChange();
	}

	private int sessions(long user) {
		return this.jdbc.sql("select count(*) from spring_session where principal_name = :name")
			.param("name", Long.toString(user))
			.query(Integer.class)
			.single();
	}

	private static Cookie cookie(MockHttpServletResponse response, String name) {
		Cookie found = cookieOrNull(response, name);
		assertThat(found).as("Set-Cookie " + name).isNotNull();
		return found;
	}

	private static @Nullable Cookie cookieOrNull(MockHttpServletResponse response, String name) {
		Cookie cookie = response.getCookie(name);
		if (cookie != null && !cookie.getValue().isEmpty()) {
			return cookie;
		}
		return response.getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(header -> header.startsWith(name + "="))
			.reduce((a, b) -> b)
			.map(header -> header.split(";", 2)[0])
			.map(pair -> pair.substring(pair.indexOf('=') + 1))
			.filter(value -> !value.isEmpty())
			.map(value -> new Cookie(name, value))
			.orElse(null);
	}

	private record Session(Cookie session, String csrf) {

		Session(Cookie session, Cookie csrf) {
			this(session, csrf.getValue());
		}

	}

	private record Recovered(String result, @Nullable String recoveryCode, @Nullable Long retryAfterSeconds,
			@Nullable Cookie session) {
	}

}
