package net.yozmnews.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.servlet.http.Cookie;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.AccountRepository;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 계정 경로의 사용자 격리 — 사용자 A 의 세션으로 비밀번호를 바꾸거나 복구 코드를 다시 받아도 B 의 비밀번호 · 복구 코드는 그대로다. 세션 없이 부르면
 * 401 이다(docs/qa/test-strategy.md §13).
 * <p>
 * 지키는 규칙: user-R6
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
public class AccountScopeTests {

	private static final Instant T = Instant.parse("2026-11-13T00:00:00Z");

	private static final String CODE = "0123-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ";

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
	}

	@Test
	void userAChangesOnlyTheirOwnPasswordAndRecoveryCode() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("account-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("account-b@example.com");
		this.recoveryCodes.replace(a.id(), RecoveryCode.parse(CODE).orElseThrow().digest(), T);
		this.recoveryCodes.replace(b.id(), RecoveryCode.parse(CODE).orElseThrow().digest(), T);
		String bHash = this.credentials.findById(b.id().value()).orElseThrow().passwordHash();

		this.mockMvc.perform(post("/api/account/recovery-code").with(a).contentType(MediaType.APPLICATION_JSON).content("""
				{"currentPassword":"%s"}""".formatted(SignedInUsers.PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("ISSUED"));
		this.mockMvc.perform(put("/api/account/password").with(a).contentType(MediaType.APPLICATION_JSON).content("""
				{"currentPassword":"%s","newPassword":"a brand new password"}""".formatted(SignedInUsers.PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("CHANGED"));

		assertThat(this.credentials.findById(b.id().value()).orElseThrow().passwordHash())
			.as("A 의 변경은 B 의 비밀번호를 바꾸지 않는다")
			.isEqualTo(bHash);
		assertThat(this.recoveryCodes.find(b.id()).orElseThrow().matches(RecoveryCode.parse(CODE).orElseThrow().digest()))
			.as("A 의 재발급은 B 의 복구 코드를 바꾸지 않는다")
			.isTrue();
		assertThat(this.recoveryCodes.find(a.id()).orElseThrow().matches(RecoveryCode.parse(CODE).orElseThrow().digest()))
			.isFalse();
	}

	@Test
	void accountPathsNeedASession() throws Exception {
		Cookie csrf = new Cookie("XSRF-TOKEN", "account-scope");

		this.mockMvc.perform(put("/api/account/password").cookie(csrf)
			.header("X-XSRF-TOKEN", "account-scope")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"a","newPassword":"b"}"""))
			.andExpect(status().isUnauthorized());
		this.mockMvc.perform(post("/api/account/recovery-code").cookie(csrf)
			.header("X-XSRF-TOKEN", "account-scope")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"a"}"""))
			.andExpect(status().isUnauthorized());
	}

}
