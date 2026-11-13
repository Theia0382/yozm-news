package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import jakarta.servlet.http.Cookie;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.user.AccountAdministration;
import net.yozmnews.user.AccountCreationResult;
import net.yozmnews.user.InitialPasswordResetResult;
import net.yozmnews.user.PasswordProblem;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자의 계정 운영 — 만든 계정은 일반 사용자 · 강제 변경이라 첫 로그인에서 비밀번호를 바꿔야 하고, 첫 로그인 전 계정의 초기 비밀번호를 다시 정하면
 * 앞 세션이 끝나며, 본인이 바꾼 계정은 다시 정하지 않는다.
 * <p>
 * 지키는 규칙: user-R28 · user-R29 · user-R35
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OperatorAccountServiceTests {

	private static final String INITIAL = "initial horse battery";

	private static final String CHOSEN = "my own horse battery";

	private static final String CSRF = "operator-account-tests";

	@Autowired
	AccountAdministration administration;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from users").update();
		this.jdbc.sql("delete from login_failures").update();
	}

	@Test
	void createdAccountMustChangeItsPasswordOnTheFirstSignIn() throws Exception {
		AccountCreationResult result = this.administration.create(" Kim@Example.com ", " 김요즘 ", INITIAL);

		assertThat(result).isInstanceOf(AccountCreationResult.Created.class);
		Map<String, Object> row = this.jdbc.sql("""
				select u.email, u.display_name, u.role, c.must_change from users u
				join user_password_credentials c on c.user_id = u.id
				""").query().singleRow();
		assertThat(row).containsEntry("email", "kim@example.com")
			.containsEntry("display_name", "김요즘")
			.containsEntry("role", "USER")
			.containsEntry("must_change", true);
		assertThat(this.jdbc.sql("select count(*) from user_recovery_codes").query(Integer.class).single()).isZero();

		Cookie[] session = signIn("kim@example.com", INITIAL);
		this.mockMvc.perform(get("/api/me").cookie(session)).andExpect(jsonPath("$.passwordChangeRequired").value(true));
		this.mockMvc.perform(get("/api/notices").cookie(session)).andExpect(status().isForbidden());
		this.mockMvc.perform(put("/api/account/password").cookie(session)
			.header("X-XSRF-TOKEN", csrf(session))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"%s","newPassword":"%s"}""".formatted(INITIAL, CHOSEN)))
			.andExpect(jsonPath("$.result").value("CHANGED"))
			.andExpect(jsonPath("$.recoveryCode").isNotEmpty());
	}

	@Test
	void creationTellsTheOperatorWhatWasWrong() {
		this.administration.create("kim@example.com", "김요즘", INITIAL);

		assertThat(this.administration.create("KIM@example.com", "다른 김요즘", INITIAL))
			.isEqualTo(new AccountCreationResult.EmailTaken());
		assertThat(this.administration.create("not an email", "이름", INITIAL))
			.isEqualTo(new AccountCreationResult.EmailMalformed());
		assertThat(this.administration.create("lee@example.com", " ", INITIAL))
			.isEqualTo(new AccountCreationResult.DisplayNameMalformed());
		assertThat(this.administration.create("lee@example.com", "이", "short"))
			.isEqualTo(new AccountCreationResult.PasswordRejected(PasswordProblem.TOO_SHORT));
		assertThat(this.jdbc.sql("select count(*) from users").query(Integer.class).single()).isOne();
	}

	@Test
	void resettingAPendingAccountsInitialPasswordEndsItsSessions() throws Exception {
		this.administration.create("park@example.com", "박", INITIAL);
		Cookie[] before = signIn("park@example.com", INITIAL);

		assertThat(this.administration.resetInitialPassword(" PARK@example.com", "another initial horse"))
			.isEqualTo(new InitialPasswordResetResult.Reset());

		this.mockMvc.perform(get("/api/me").cookie(before)).andExpect(jsonPath("$.result").value("ANONYMOUS"));
		assertThat(signInResult("park@example.com", INITIAL)).isEqualTo("REJECTED");
		Cookie[] after = signIn("park@example.com", "another initial horse");
		this.mockMvc.perform(get("/api/me").cookie(after)).andExpect(jsonPath("$.passwordChangeRequired").value(true));
	}

	@Test
	void anAccountWhoseOwnerChangedThePasswordIsNotReset() throws Exception {
		this.administration.create("choi@example.com", "최", INITIAL);
		Cookie[] session = signIn("choi@example.com", INITIAL);
		this.mockMvc.perform(put("/api/account/password").cookie(session)
			.header("X-XSRF-TOKEN", csrf(session))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"%s","newPassword":"%s"}""".formatted(INITIAL, CHOSEN)))
			.andExpect(jsonPath("$.result").value("CHANGED"));
		String hash = this.jdbc.sql("select password_hash from user_password_credentials").query(String.class).single();

		assertThat(this.administration.resetInitialPassword("choi@example.com", "another initial horse"))
			.isEqualTo(new InitialPasswordResetResult.NotPending());
		assertThat(this.jdbc.sql("select password_hash from user_password_credentials").query(String.class).single())
			.isEqualTo(hash);
		assertThat(this.administration.resetInitialPassword("nobody@example.com", "another initial horse"))
			.isEqualTo(new InitialPasswordResetResult.NoAccount());
		assertThat(this.administration.resetInitialPassword("choi@example.com", "short"))
			.isEqualTo(new InitialPasswordResetResult.PasswordRejected(PasswordProblem.TOO_SHORT));
	}

	private Cookie[] signIn(String email, String password) throws Exception {
		MockHttpServletResponse response = this.mockMvc
			.perform(post("/api/session").cookie(new Cookie("XSRF-TOKEN", CSRF))
				.header("X-XSRF-TOKEN", CSRF)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s","keep":false}""".formatted(email, password)))
			.andExpect(jsonPath("$.result").value("SIGNED_IN"))
			.andReturn()
			.getResponse();
		return new Cookie[] { cookie(response, "SESSION"), cookie(response, "XSRF-TOKEN") };
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

	private static String csrf(Cookie[] session) {
		return session[1].getValue();
	}

	private static Cookie cookie(MockHttpServletResponse response, String name) {
		String header = response.getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(value -> value.startsWith(name + "=") && !value.startsWith(name + "=;"))
			.reduce((a, b) -> b)
			.orElseThrow();
		String pair = header.split(";", 2)[0];
		return new Cookie(name, pair.substring(pair.indexOf('=') + 1));
	}

}
