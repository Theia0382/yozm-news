package net.yozmnews.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.servlet.http.Cookie;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.application.PasswordHashing;
import net.yozmnews.account.domain.account.AccountRepository;
import net.yozmnews.account.domain.credential.PasswordCredentialRepository;
import net.yozmnews.account.domain.guard.ClientAddress;
import net.yozmnews.account.domain.humancheck.HumanCheckResult;
import net.yozmnews.account.domain.humancheck.HumanCheckToken;
import net.yozmnews.account.domain.humancheck.HumanVerifier;
import net.yozmnews.llm.ScriptedProviderClients;
import net.yozmnews.platform.GuardedAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 봇 차단 — 봇 차단을 켠 앱(Turnstile, 검증기는 답을 정하는 대역)에서 로그인 · 복구 · 공개 가입은 헤더 {@code X-Human-Check} 의 토큰을 요청
 * 제한 뒤 · 처리기 앞에서 확인한다. 거부면 403 {@code human-check-failed}, 검증 서비스에 닿지 못하면 처리기로 넘어간다. 대상이 아닌 처리기는
 * 토큰 없이 처리기 결과다. 요청 제한에 걸린 요청은 검증기를 부르지 않는다. 헤더 이름 · 문제 유형은 리터럴이다. 끈 앱(기본)에서 요청 제한만
 * 남는 것은 {@code RequestGuardTests} 가 본다.
 * <p>
 * 지키는 규칙: account-R32 · account-R33
 */
@Import({ TestcontainersConfiguration.class, ScriptedProviderClients.Config.class,
		HumanCheckGuardTests.ScriptedVerifier.Config.class })
@SpringBootTest(properties = { "yozmnews.human-check.provider=turnstile",
		"yozmnews.human-check.turnstile.site-key=1x00000000000000000000AA" })
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class HumanCheckGuardTests {

	private static final String CSRF = "human-check-guard-tests";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ScriptedVerifier verifier;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	PasswordHashing hashing;

	@BeforeEach
	void reset() {
		this.verifier.answer = new HumanCheckResult.Passed();
		this.verifier.asked.clear();
	}

	@Test
	void signInRecoveryAndSignUpWithoutATokenAreRefused() throws Exception {
		assertRefused(this.mockMvc.perform(publicPost("/api/session", "192.0.2.31", null, """
				{"email":"check-sign-in@example.com","password":"wrong password here","keep":false}""")).andReturn().getResponse());
		assertRefused(this.mockMvc.perform(publicPost("/api/account-recoveries", "192.0.2.31", null, """
				{"email":"check-recovery@example.com","recoveryCode":"not-a-code","newPassword":"new password here",
				"keep":false}""")).andReturn().getResponse());
		assertRefused(this.mockMvc.perform(publicPost("/api/sign-ups", "192.0.2.31", null, """
				{"email":"check-sign-up@example.com","displayName":"가입","password":"sign up password","keep":false}"""))
			.andReturn()
			.getResponse());
		assertThat(this.verifier.asked).isEmpty();
	}

	@Test
	void aPassedTokenReachesTheHandlerWithTheActionAndClientAddress() throws Exception {
		MockHttpServletResponse response = this.mockMvc.perform(publicPost("/api/session", "192.0.2.32", "0.passed",
				"""
						{"email":"check-passed@example.com","password":"wrong password here","keep":false}"""))
			.andReturn()
			.getResponse();

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("\"result\":\"REJECTED\"");
		assertThat(this.verifier.asked).containsExactly("SIGN_IN 192.0.2.32");
	}

	@Test
	void aRejectedTokenIsRefusedAndLogged(CapturedOutput output) throws Exception {
		this.verifier.answer = new HumanCheckResult.Rejected("timeout-or-duplicate");

		assertRefused(this.mockMvc.perform(publicPost("/api/sign-ups", "192.0.2.33", "0.spent", """
				{"email":"check-spent@example.com","displayName":"가입","password":"sign up password","keep":false}"""))
			.andReturn()
			.getResponse());
		assertThat(this.verifier.asked).containsExactly("SIGN_UP 192.0.2.33");
		assertThat(output.getOut()).contains("사람 확인을 거부했다 — 동작 SIGN_UP · 까닭 timeout-or-duplicate")
			.doesNotContain("0.spent");
	}

	@Test
	void anUnreachableVerifierLetsTheRequestThrough() throws Exception {
		this.verifier.answer = new HumanCheckResult.Unavailable("시험 — 닿지 못함");

		MockHttpServletResponse response = this.mockMvc
			.perform(publicPost("/api/account-recoveries", "192.0.2.34", "0.any", """
					{"email":"check-unreachable@example.com","recoveryCode":"not-a-code","newPassword":"new password here",
					"keep":false}"""))
			.andReturn()
			.getResponse();

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(this.verifier.asked).containsExactly("ACCOUNT_RECOVERY 192.0.2.34");
	}

	@Test
	void handlersOutsideTheBotCheckNeedNoToken() throws Exception {
		assertThat(this.mockMvc.perform(publicPost("/api/invitation-checks", "192.0.2.35", null, """
				{"token":"not-a-real-invitation-token"}""")).andReturn().getResponse().getStatus()).isEqualTo(200);
		SignedInUsers.SignedInUser user = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing)
			.signIn("check-keys@example.com");
		assertThat(this.mockMvc
			.perform(put("/api/llm-keys/OPENAI").with(user)
				.header("X-Real-IP", "192.0.2.35")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"sk-check-registered-0123456789"}"""))
			.andReturn()
			.getResponse()
			.getStatus()).isEqualTo(200);
		// 로그인 시험 사용자를 만드는 로그인 하나만 검증기에 물었다
		assertThat(this.verifier.asked).hasSize(1).allMatch(asked -> asked.startsWith("SIGN_IN "));
	}

	@Test
	void aRateLimitedRequestIsNotSentToTheVerifier() throws Exception {
		int limitedAt = 0;
		for (int i = 1; i <= 11 && limitedAt == 0; i++) {
			MockHttpServletResponse response = this.mockMvc
				.perform(publicPost("/api/sign-ups", "192.0.2.36", "0.passed", """
						{"email":"check-limited@example.com","displayName":"가입","password":"sign up password","keep":false}"""))
				.andReturn()
				.getResponse();
			if (response.getStatus() == 429) {
				limitedAt = i;
			}
			else {
				assertThat(this.verifier.asked).as("요청 %d", i).hasSize(i);
			}
		}

		assertThat(limitedAt).as("429 가 나오지 않았다").isPositive();
		// 429 로 끝난 요청은 검증기에 묻지 않았다 — 물은 수는 그 앞의 요청 수다
		assertThat(this.verifier.asked).hasSize(limitedAt - 1).allMatch("SIGN_UP 192.0.2.36"::equals);
	}

	private static void assertRefused(MockHttpServletResponse response) throws Exception {
		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentType()).isEqualTo("application/problem+json");
		JsonNode problem = JsonMapper.builder().build().readTree(response.getContentAsString(StandardCharsets.UTF_8));
		assertThat(problem.path("type").asString()).isEqualTo("urn:yozmnews:problem:human-check-failed");
		assertThat(problem.path("title").asString()).isEqualTo("사람 확인에 실패했다");
		assertThat(problem.path("status").asInt()).isEqualTo(403);
	}

	private static MockHttpServletRequestBuilder publicPost(String path, String clientAddress, String token,
			String body) {
		MockHttpServletRequestBuilder request = post(path).header("X-Real-IP", clientAddress)
			.cookie(new Cookie("XSRF-TOKEN", CSRF))
			.header("X-XSRF-TOKEN", CSRF)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
		return (token == null) ? request : request.header("X-Human-Check", token);
	}

	/** 답을 정하고 물은 동작 · 주소를 남기는 검증기 */
	static final class ScriptedVerifier implements HumanVerifier {

		volatile HumanCheckResult answer = new HumanCheckResult.Passed();

		final List<String> asked = new CopyOnWriteArrayList<>();

		@Override
		public HumanCheckResult verify(HumanCheckToken token, GuardedAction action, ClientAddress client) {
			this.asked.add(action.name() + " " + client.literal());
			return this.answer;
		}

		@TestConfiguration(proxyBeanMethods = false)
		static class Config {

			@Bean
			ScriptedVerifier scriptedVerifier() {
				return new ScriptedVerifier();
			}

		}

	}

}
