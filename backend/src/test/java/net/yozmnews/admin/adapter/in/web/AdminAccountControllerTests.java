package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.AccountCreationResult;
import net.yozmnews.user.InitialPasswordResetResult;
import net.yozmnews.user.PasswordProblem;
import net.yozmnews.user.application.WebSliceSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 계정 — 생성 · 초기 비밀번호 다시 정하기의 판별자, 형식 · 규칙 위반 400, 없는 계정 404, 토큰.
 * <p>
 * 지키는 규칙: admin-R1 · admin-R2 · admin-R3 · user-R35
 */
@WebMvcTest(controllers = AdminAccountController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeAccountAdministration.Config.class })
class AdminAccountControllerTests {

	private static final String CREATE = """
			{"email":"kim@example.com","displayName":"김요즘","initialPassword":"initial horse battery"}""";

	private static final String RESET = """
			{"email":"kim@example.com","initialPassword":"another initial horse"}""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeAccountAdministration accounts;

	@BeforeEach
	void reset() {
		this.accounts.reset();
	}

	@Test
	void creationCarriesTheUserIdOrSaysTheEmailIsTaken() throws Exception {
		this.accounts.creation = new AccountCreationResult.Created(new UserId(42));
		this.mockMvc.perform(json(post("/api/admin/accounts"), CREATE))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"CREATED","userId":42}""", JsonCompareMode.STRICT));

		this.accounts.creation = new AccountCreationResult.EmailTaken();
		this.mockMvc.perform(json(post("/api/admin/accounts"), CREATE))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"EMAIL_TAKEN","userId":null}""", JsonCompareMode.STRICT));
		assertThat(this.accounts.calls).containsExactly("create kim@example.com 김요즘", "create kim@example.com 김요즘");
	}

	@Test
	void malformedFieldsAreABadRequestNamingTheRule() throws Exception {
		this.accounts.creation = new AccountCreationResult.EmailMalformed();
		this.mockMvc.perform(json(post("/api/admin/accounts"), CREATE))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"status":400,"detail":"이메일 형식이 아니다"}"""));
		this.accounts.creation = new AccountCreationResult.DisplayNameMalformed();
		this.mockMvc.perform(json(post("/api/admin/accounts"), CREATE))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"status":400,"detail":"표시 이름은 앞뒤 공백을 빼고 1 ~ 50자다"}"""));
		this.accounts.creation = new AccountCreationResult.PasswordRejected(PasswordProblem.TOO_LONG);
		this.mockMvc.perform(json(post("/api/admin/accounts"), CREATE))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"status":400,"detail":"초기 비밀번호가 길다 — 8 ~ 128자다"}"""));
		this.mockMvc.perform(json(post("/api/admin/accounts"), """
				{"email":"kim@example.com","displayName":"김요즘"}"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void resettingSaysWhetherTheAccountWasStillPending() throws Exception {
		this.accounts.reset = new InitialPasswordResetResult.Reset();
		this.mockMvc.perform(json(put("/api/admin/accounts/initial-password"), RESET))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"RESET"}""", JsonCompareMode.STRICT));

		this.accounts.reset = new InitialPasswordResetResult.NotPending();
		this.mockMvc.perform(json(put("/api/admin/accounts/initial-password"), RESET))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"NOT_PENDING"}""", JsonCompareMode.STRICT));
		assertThat(this.accounts.calls).containsExactly("resetInitialPassword kim@example.com",
				"resetInitialPassword kim@example.com");
	}

	@Test
	void resettingAMissingAccountIsNotFoundAndABadPasswordIsABadRequest() throws Exception {
		this.accounts.reset = new InitialPasswordResetResult.NoAccount();
		this.mockMvc.perform(json(put("/api/admin/accounts/initial-password"), RESET))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"status":404,"detail":"그 이메일의 계정이 없다"}"""));
		this.accounts.reset = new InitialPasswordResetResult.PasswordRejected(PasswordProblem.TOO_SHORT);
		this.mockMvc.perform(json(put("/api/admin/accounts/initial-password"), RESET))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"status":400,"detail":"초기 비밀번호가 짧다 — 8 ~ 128자다"}"""));
	}

	@Test
	void withoutTheTokenNothingIsCalled() throws Exception {
		this.mockMvc.perform(post("/api/admin/accounts").contentType(MediaType.APPLICATION_JSON).content(CREATE))
			.andExpect(status().isUnauthorized());
		this.mockMvc
			.perform(put("/api/admin/accounts/initial-password").contentType(MediaType.APPLICATION_JSON).content(RESET))
			.andExpect(status().isUnauthorized());
		assertThat(this.accounts.calls).isEmpty();
	}

}
