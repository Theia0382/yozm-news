package net.yozmnews.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import jakarta.servlet.http.Cookie;
import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.llm.ScriptedProviderClients;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 요청 제한 — 앱 전체를 실제 PostgreSQL 로 띄워, 표시가 있는 여덟 처리기에 같은 클라이언트 주소({@code X-Real-IP}) · 같은 세션
 * 사용자로 요청을 거듭 보내면 429 {@code rate-limited} 문제 응답과 {@code Retry-After} 가 나오는지 본다. 고정 창이라 짧은 시간의 요청은
 * 많아야 경계 하나를 넘으므로, 한도의 두 배 + 1 번 안에 429 가 반드시 나온다 — 정확히 몇 번째인지는 고정 시계의
 * {@code RequestLimiterTests} 가 본다. 헤더 이름 · 문제 유형은 리터럴이다.
 * <p>
 * 지키는 규칙: user-R37 · user-R38 · user-R39 · user-R40
 */
@Import({ TestcontainersConfiguration.class, ScriptedProviderClients.Config.class })
@SpringBootTest
@AutoConfigureMockMvc
class RequestGuardTests {

	private static final String CSRF = "request-guard-tests";

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

	private SignedInUsers users;

	@BeforeEach
	void users() {
		this.users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
	}

	@Test
	void signInFromOneAddressIsLimited() throws Exception {
		assertLimited("/api/session", 30, () -> publicPost("/api/session", "192.0.2.11", """
				{"email":"guard-sign-in@example.com","password":"wrong password here","keep":false}"""), 600);
	}

	@Test
	void accountRecoveryFromOneAddressIsLimited() throws Exception {
		assertLimited("/api/account-recoveries", 10, () -> publicPost("/api/account-recoveries", "192.0.2.12", """
				{"email":"guard-recovery@example.com","recoveryCode":"not-a-code","newPassword":"new password here",
				"keep":false}"""), 3600);
	}

	@Test
	void invitationCheckFromOneAddressIsLimited() throws Exception {
		assertLimited("/api/invitation-checks", 30,
				() -> publicPost("/api/invitation-checks", "192.0.2.13", """
						{"token":"not-a-real-invitation-token"}"""), 3600);
	}

	@Test
	void invitationAcceptanceFromOneAddressIsLimited() throws Exception {
		assertLimited("/api/invitation-acceptances", 10, () -> publicPost("/api/invitation-acceptances", "192.0.2.14",
				"""
						{"token":"not-a-real-invitation-token","email":"guard-invited@example.com",
						"displayName":"초대","password":"invited password","keep":false}"""), 3600);
	}

	@Test
	void signUpFromOneAddressIsLimited() throws Exception {
		assertLimited("/api/sign-ups", 5, () -> publicPost("/api/sign-ups", "192.0.2.15", """
				{"email":"guard-sign-up@example.com","displayName":"가입","password":"sign up password","keep":false}"""),
				3600);
	}

	@Test
	void llmKeyRegistrationAndCheckShareOneLimitPerUser() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("guard-keys@example.com");
		boolean[] flip = { false };
		// 등록과 다시 검증을 번갈아 보낸다 — 둘이 한도 하나를 함께 쓴다
		assertLimited("/api/llm-keys/{provider}", 20, () -> {
			flip[0] = !flip[0];
			return flip[0]
					? put("/api/llm-keys/OPENAI").with(user)
						.header("X-Real-IP", "192.0.2.16")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"key":"sk-guard-registered-0123456789"}""")
					: post("/api/llm-keys/OPENAI/checks").with(user).header("X-Real-IP", "192.0.2.16");
		}, 3600);
	}

	@Test
	void feedbackIsLimitedPerUser() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("guard-feedback@example.com");
		LlmKeyRows.give(this.jdbc, user.id().value());
		try {
			// 인용되지 않은 기사라 판정 결과는 NOT_IN_DIGEST(200)다 — 결과와 무관하게 센다
			assertLimited("/api/feedback/{articleId}", 750, () -> put("/api/feedback/1").with(user)
				.header("X-Real-IP", "192.0.2.20")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"value":"LIKE","digestId":1}"""), 600);
			// 대조 — 표시는 누르기 · 되돌리기에만 있다. 같은 사용자의 현재 상태 조회는 막히지 않는다
			assertThat(this.mockMvc.perform(get("/api/feedback").param("articleIds", "1").with(user))
				.andReturn()
				.getResponse()
				.getStatus()).isEqualTo(200);
		}
		finally {
			// 열 수 없는 자리 값의 키 행을 다른 시험에 남기지 않는다
			this.jdbc.sql("delete from llm_credentials where user_id = :id").param("id", user.id().value()).update();
		}
	}

	@Test
	void anotherAddressForEachRequestIsNeverLimited() throws Exception {
		for (int i = 1; i <= 11; i++) {
			MockHttpServletResponse response = this.mockMvc
				.perform(publicPost("/api/sign-ups", "198.51.100." + i, """
						{"email":"guard-spread@example.com","displayName":"가입","password":"sign up password","keep":false}"""))
				.andReturn()
				.getResponse();
			assertThat(response.getStatus()).as("요청 %d", i).isEqualTo(200);
		}
	}

	@Test
	void signInIsCountedByAddressNotByEmail() throws Exception {
		// 같은 이메일이어도 주소가 다르면 IP 한도에 걸리지 않는다 — 이메일 축은 실패 지연이 맡는다(200 WAIT)
		for (int i = 1; i <= 31; i++) {
			MockHttpServletResponse response = this.mockMvc
				.perform(publicPost("/api/session", "198.51.100." + (100 + i), """
						{"email":"guard-one-email@example.com","password":"wrong password here","keep":false}"""))
				.andReturn()
				.getResponse();
			assertThat(response.getStatus()).as("요청 %d", i).isEqualTo(200);
		}
	}

	@Test
	void requestsWithoutCsrfAreRejectedBeforeBeingCounted() throws Exception {
		for (int i = 1; i <= 6; i++) {
			assertThat(this.mockMvc
				.perform(post("/api/sign-ups").header("X-Real-IP", "192.0.2.17")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email":"guard-no-csrf@example.com","displayName":"가입","password":"sign up password","keep":false}"""))
				.andReturn()
				.getResponse()
				.getStatus()).isEqualTo(403);
		}
		// 대조 — 같은 주소의 정상 요청 다섯 번(한도)이 지난다
		for (int i = 1; i <= 5; i++) {
			assertThat(this.mockMvc.perform(publicPost("/api/sign-ups", "192.0.2.17", """
					{"email":"guard-no-csrf@example.com","displayName":"가입","password":"sign up password","keep":false}"""))
				.andReturn()
				.getResponse()
				.getStatus()).as("요청 %d", i).isEqualTo(200);
		}
	}

	@Test
	void requestsWithoutASessionAreRejectedBeforeBeingCounted() throws Exception {
		for (int i = 1; i <= 61; i++) {
			assertThat(this.mockMvc
				.perform(post("/api/llm-keys/OPENAI/checks").header("X-Real-IP", "192.0.2.18")
					.cookie(new Cookie("XSRF-TOKEN", CSRF))
					.header("X-XSRF-TOKEN", CSRF))
				.andReturn()
				.getResponse()
				.getStatus()).isEqualTo(401);
		}
		// 대조 — 같은 주소에서 사용자 한도(20)만큼의 요청이 지난다. 세션 없는 61번을 셌다면 주소 한도(60)에 이미 걸렸다
		SignedInUsers.SignedInUser user = this.users.signIn("guard-no-session@example.com");
		for (int i = 1; i <= 20; i++) {
			assertThat(this.mockMvc
				.perform(post("/api/llm-keys/OPENAI/checks").with(user).header("X-Real-IP", "192.0.2.18"))
				.andReturn()
				.getResponse()
				.getStatus()).as("요청 %d", i).isEqualTo(200);
		}
	}

	@Test
	void anUnmarkedHandlerIsNotLimited() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("guard-unmarked@example.com");
		for (int i = 1; i <= 70; i++) {
			assertThat(this.mockMvc.perform(get("/api/llm-keys").with(user).header("X-Real-IP", "192.0.2.19"))
				.andReturn()
				.getResponse()
				.getStatus()).as("요청 %d", i).isEqualTo(200);
		}
	}

	/**
	 * 한도의 두 배 + 1 번 안에 429 가 나오고, 그 응답이 문제 응답 꼴이며 {@code Retry-After} 가 1 이상 창 길이 이하인지 본다. 429 전의
	 * 응답은 모두 처리기의 결과(200)다.
	 */
	private void assertLimited(String path, int limit, Supplier<MockHttpServletRequestBuilder> request,
			long windowSeconds) throws Exception {
		@Nullable MockHttpServletResponse limited = null;
		for (int i = 1; i <= 2 * limit + 1 && limited == null; i++) {
			MockHttpServletResponse response = this.mockMvc.perform(request.get()).andReturn().getResponse();
			if (response.getStatus() == 429) {
				limited = response;
			}
			else {
				assertThat(response.getStatus()).as("%s 요청 %d", path, i).isEqualTo(200);
			}
		}
		assertThat(limited).as("%s — 429 가 나오지 않았다", path).isNotNull();
		assertThat(limited.getContentType()).isEqualTo("application/problem+json");
		JsonNode problem = JsonMapper.builder()
			.build()
			.readTree(limited.getContentAsString(StandardCharsets.UTF_8));
		assertThat(problem.path("type").asString()).isEqualTo("urn:yozmnews:problem:rate-limited");
		assertThat(problem.path("status").asInt()).isEqualTo(429);
		assertThat(problem.path("title").asString()).isEqualTo("요청이 너무 많다");
		assertThat(limited.getHeader("Cache-Control")).isEqualTo("no-store");
		assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isBetween(1L, windowSeconds);
	}

	private static MockHttpServletRequestBuilder publicPost(String path, String clientAddress, String body) {
		return post(path).header("X-Real-IP", clientAddress)
			.cookie(new Cookie("XSRF-TOKEN", CSRF))
			.header("X-XSRF-TOKEN", CSRF)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

}
