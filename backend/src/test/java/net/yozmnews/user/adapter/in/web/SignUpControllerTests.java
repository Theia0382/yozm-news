package net.yozmnews.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.InvitationAdministration;
import net.yozmnews.user.IssuedInvitation;
import net.yozmnews.user.SignUpPolicyAdministration;
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
 * 가입 — 실제 PostgreSQL 의 세션 위에서 공개 가입을 끈 상태 · 켠 상태, 초대 상태 확인과 초대 가입, 가입할 수 없는 이메일의 같은 결과, 가입 뒤의
 * 새 세션과 한 번 나가는 복구 코드를 본다.
 * <p>
 * 지키는 규칙: user-R8 · user-R11 · user-R28 · user-R30 · user-R31 · user-R32 · user-R33
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SignUpControllerTests {

	private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

	private static final String PASSWORD = "correct horse battery";

	private static final String CODE_PATTERN = "^[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){7}$";

	private static final String CSRF = "sign-up-controller-tests";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	SignUpPolicyAdministration policy;

	@Autowired
	InvitationAdministration invitations;

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
		this.jdbc.sql("delete from user_invitations").update();
		this.jdbc.sql("delete from users").update();
		this.policy.change(false, ChangeActor.OPERATOR);
	}

	@Test
	void closedSignUpRefusesAndTheScreenSeesItClosed() throws Exception {
		this.mockMvc.perform(get("/api/sign-up-policy"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"open":false}""", JsonCompareMode.STRICT));

		this.mockMvc.perform(signUp("alice@example.com", "앨리스", PASSWORD))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"CLOSED","recoveryCode":null,"invitation":null,"passwordProblem":null}
					""", JsonCompareMode.STRICT));
		assertThat(users()).isZero();
	}

	@Test
	void openSignUpMakesAUserSignsInAndShowsTheRecoveryCodeOnce() throws Exception {
		this.policy.change(true, ChangeActor.OPERATOR);
		this.mockMvc.perform(get("/api/sign-up-policy")).andExpect(content().json("""
				{"open":true}""", JsonCompareMode.STRICT));

		MockHttpServletResponse response = this.mockMvc.perform(signUp(" Alice@Example.com ", " 앨리스 ", PASSWORD))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("SIGNED_UP"))
			.andExpect(jsonPath("$.recoveryCode").value(org.hamcrest.Matchers.matchesPattern(CODE_PATTERN)))
			.andExpect(jsonPath("$.invitation").isEmpty())
			.andExpect(jsonPath("$.passwordProblem").isEmpty())
			.andReturn()
			.getResponse();

		long user = userId("alice@example.com");
		this.mockMvc.perform(get("/api/me").cookie(cookie(response, "SESSION")))
			.andExpect(content().json("""
					{"result":"SIGNED_IN","userId":%d,"email":"alice@example.com","displayName":"앨리스","role":"USER",
					 "passwordChangeRequired":false}
					""".formatted(user), JsonCompareMode.STRICT));
		String code = com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.recoveryCode");
		assertThat(this.recoveryCodes.find(new UserId(user)).orElseThrow()
			.matches(RecoveryCode.parse(code).orElseThrow().digest())).isTrue();

		// 다시 닫으면 다음 가입부터 막힌다
		this.policy.change(false, ChangeActor.OPERATOR);
		this.mockMvc.perform(signUp("bob@example.com", "밥", PASSWORD)).andExpect(jsonPath("$.result").value("CLOSED"));
		assertThat(users()).isOne();
	}

	@Test
	void takenEmailIsUnavailableInOpenAndInvitedSignUpAlike() throws Exception {
		this.policy.change(true, ChangeActor.OPERATOR);
		existing("carol@example.com");
		String token = token();

		String open = body(signUp("CAROL@example.com", "캐럴", PASSWORD));
		String invited = body(acceptance(token, "carol@example.com", "캐럴", PASSWORD));

		assertThat(open).isEqualTo("""
				{"result":"EMAIL_UNAVAILABLE","recoveryCode":null,"invitation":null,"passwordProblem":null}""");
		assertThat(invited).isEqualTo(open);
		assertThat(check(token)).as("가입하지 못한 초대는 쓰지 않는다").isEqualTo("VALID");
		assertThat(users()).isOne();
	}

	@Test
	void malformedFieldsAreToldApart() throws Exception {
		this.policy.change(true, ChangeActor.OPERATOR);

		this.mockMvc.perform(signUp("not an email", "이름", PASSWORD))
			.andExpect(jsonPath("$.result").value("EMAIL_MALFORMED"));
		this.mockMvc.perform(signUp("dave@example.com", "   ", PASSWORD))
			.andExpect(jsonPath("$.result").value("DISPLAY_NAME_MALFORMED"));
		this.mockMvc.perform(signUp("dave@example.com", "가".repeat(51), PASSWORD))
			.andExpect(jsonPath("$.result").value("DISPLAY_NAME_MALFORMED"));
		this.mockMvc.perform(signUp("dave@example.com", "데이브", "short"))
			.andExpect(content().json("""
					{"result":"PASSWORD_REJECTED","recoveryCode":null,"invitation":null,"passwordProblem":"TOO_SHORT"}
					""", JsonCompareMode.STRICT));
		assertThat(users()).isZero();
	}

	@Test
	void invitationSignsUpEvenWhileOpenSignUpIsClosedAndOnlyOnce() throws Exception {
		String token = token();
		assertThat(check(token)).isEqualTo("VALID");

		MockHttpServletResponse response = this.mockMvc.perform(acceptance(token, "erin@example.com", "에린", PASSWORD))
			.andExpect(jsonPath("$.result").value("SIGNED_UP"))
			.andExpect(jsonPath("$.recoveryCode").value(org.hamcrest.Matchers.matchesPattern(CODE_PATTERN)))
			.andReturn()
			.getResponse();

		this.mockMvc.perform(get("/api/me").cookie(cookie(response, "SESSION")))
			.andExpect(jsonPath("$.email").value("erin@example.com"))
			.andExpect(jsonPath("$.role").value("USER"));
		assertThat(check(token)).isEqualTo("USED");
		assertThat(body(acceptance(token, "frank@example.com", "프랭크", PASSWORD))).isEqualTo("""
				{"result":"INVITATION_UNUSABLE","recoveryCode":null,"invitation":"USED","passwordProblem":null}""");
		Map<String, Object> invitation = this.jdbc.sql("select used_by, used_at from user_invitations").query().singleRow();
		assertThat(invitation.get("used_by")).isEqualTo(userId("erin@example.com"));
		assertThat(users()).isOne();
	}

	@Test
	void revokedExpiredAndUnknownInvitationsCannotSignUp() throws Exception {
		String revoked = token();
		long revokedId = this.jdbc.sql("select id from user_invitations").query(Long.class).single();
		this.invitations.revoke(revokedId);
		String expired = token();
		this.jdbc.sql("update user_invitations set expires_at = now() - interval '1 second' where revoked_at is null")
			.update();

		assertThat(check(revoked)).isEqualTo("REVOKED");
		assertThat(check(expired)).isEqualTo("EXPIRED");
		assertThat(check("AAAAAAAAAAAAAAAAAAAAAA")).isEqualTo("UNKNOWN");
		assertThat(check("not a token")).isEqualTo("UNKNOWN");
		this.mockMvc.perform(acceptance(revoked, "gina@example.com", "지나", PASSWORD))
			.andExpect(jsonPath("$.invitation").value("REVOKED"));
		this.mockMvc.perform(acceptance(expired, "gina@example.com", "지나", PASSWORD))
			.andExpect(jsonPath("$.invitation").value("EXPIRED"));
		this.mockMvc.perform(acceptance("not a token", "gina@example.com", "지나", PASSWORD))
			.andExpect(jsonPath("$.result").value("INVITATION_UNUSABLE"))
			.andExpect(jsonPath("$.invitation").value("UNKNOWN"));
		assertThat(users()).isZero();
	}

	@Test
	void signingUpFromABrowserSignedInAsAnotherAccountStartsAFreshSession() throws Exception {
		existing("hana@example.com");
		MockHttpServletResponse signedIn = this.mockMvc
			.perform(withCsrf(post("/api/session")).contentType(MediaType.APPLICATION_JSON).content("""
					{"email":"hana@example.com","password":"%s","keep":false}""".formatted(PASSWORD)))
			.andExpect(jsonPath("$.result").value("SIGNED_IN"))
			.andReturn()
			.getResponse();
		Cookie carried = cookie(signedIn, "SESSION");

		MockHttpServletResponse response = this.mockMvc
			.perform(acceptance(token(), "ian@example.com", "이안", PASSWORD).cookie(carried))
			.andExpect(jsonPath("$.result").value("SIGNED_UP"))
			.andReturn()
			.getResponse();

		Cookie fresh = cookie(response, "SESSION");
		assertThat(fresh.getValue()).isNotEqualTo(carried.getValue());
		this.mockMvc.perform(get("/api/me").cookie(fresh)).andExpect(jsonPath("$.email").value("ian@example.com"));
		this.mockMvc.perform(get("/api/me").cookie(carried)).andExpect(jsonPath("$.result").value("ANONYMOUS"));
	}

	@Test
	void signUpPathsNeedTheCsrfToken() throws Exception {
		this.mockMvc.perform(post("/api/sign-ups").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"a@example.com","displayName":"a","password":"%s","keep":false}""".formatted(PASSWORD)))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(post("/api/invitation-checks").contentType(MediaType.APPLICATION_JSON).content("""
				{"token":"AAAAAAAAAAAAAAAAAAAAAA"}"""))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(post("/api/invitation-acceptances").contentType(MediaType.APPLICATION_JSON).content("""
				{"token":"AAAAAAAAAAAAAAAAAAAAAA","email":"a@example.com","displayName":"a","password":"%s","keep":false}"""
			.formatted(PASSWORD)))
			.andExpect(status().isForbidden());
	}

	private String token() {
		return ((IssuedInvitation.Issued) this.invitations.issue(null)).token();
	}

	private String check(String token) throws Exception {
		String body = this.mockMvc.perform(withCsrf(post("/api/invitation-checks")).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"token":"%s"}""".formatted(token)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(body, "$.state");
	}

	private void existing(String email) {
		UserId id = this.accounts.save(Account.create(EmailAddress.parse(email).orElseThrow(), new DisplayName("시험"),
				AccountRole.USER, NOW)).id();
		this.credentials.save(PasswordCredential.of(id, this.hashing.hash(PASSWORD), false, NOW));
	}

	private static MockHttpServletRequestBuilder signUp(String email, String displayName, String password) {
		return withCsrf(post("/api/sign-ups")).contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","displayName":"%s","password":"%s","keep":false}""".formatted(email, displayName, password));
	}

	private static MockHttpServletRequestBuilder acceptance(String token, String email, String displayName,
			String password) {
		return withCsrf(post("/api/invitation-acceptances")).contentType(MediaType.APPLICATION_JSON).content("""
				{"token":"%s","email":"%s","displayName":"%s","password":"%s","keep":false}""".formatted(token, email,
				displayName, password));
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

	private int users() {
		return this.jdbc.sql("select count(*) from users").query(Integer.class).single();
	}

	private long userId(String email) {
		return this.jdbc.sql("select id from users where email = :email").param("email", email).query(Long.class).single();
	}

	private static Cookie cookie(MockHttpServletResponse response, String name) {
		String header = response.getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(value -> value.startsWith(name + "="))
			.reduce((a, b) -> b)
			.orElseThrow();
		String pair = header.split(";", 2)[0];
		return new Cookie(name, pair.substring(pair.indexOf('=') + 1));
	}

}
