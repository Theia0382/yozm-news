package net.yozmnews.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.Cookie;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.AccountAdministration;
import net.yozmnews.account.AccountCreationResult;
import net.yozmnews.account.InitialPasswordResetResult;
import net.yozmnews.account.application.AccountRecovery;
import net.yozmnews.account.application.PasswordHashing;
import net.yozmnews.account.application.RecoveryOutcome;
import net.yozmnews.account.application.SignIn;
import net.yozmnews.account.domain.account.Account;
import net.yozmnews.account.domain.account.AccountRepository;
import net.yozmnews.account.domain.account.AccountRole;
import net.yozmnews.account.domain.account.DisplayName;
import net.yozmnews.account.domain.account.EmailAddress;
import net.yozmnews.account.domain.credential.PasswordCredential;
import net.yozmnews.account.domain.credential.PasswordCredentialRepository;
import net.yozmnews.account.domain.recovery.RecoveryCodeRepository;
import net.yozmnews.account.domain.secret.RecoveryCode;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.AccountEvents;
import net.yozmnews.user.UserTables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 비밀번호를 바꾸는 길과 겹친 로그인 — 옛 비밀번호로 대조를 마친 로그인이 복구 재설정 · 초기 비밀번호 다시 정하기 뒤에 세션을 남기지 않는다.
 * <p>
 * 지키는 규칙: user-R18 · user-R30 · user-R35
 * <p>
 * 실제 PostgreSQL 위에서 로그인 요청(MockMvc — 세션 저장소 필터 포함)을 보내고, spy 로 순서를 정한다 — 로그인의 Argon2 대조
 * ({@link PasswordHashing})를 재설정이 커밋될 때까지 붙잡거나, 세션을 연 뒤의 다시 확인({@link SignIn#stillCurrent})이 버전을 본
 * 직후 그 트랜잭션 안에서 재설정을 돌린다. 세션이 남았는지는 세션 테이블의 사용자 이름 색인으로 센다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class SignInDuringPasswordResetTests {

	private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

	private static final String PASSWORD = "correct horse battery";

	private static final String NEW_PASSWORD = "brand new horse staple";

	private static final String CODE = "0123-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ";

	private static final String CSRF = "sign-in-during-password-reset-tests";

	private static final String REJECTED = """
			{"result":"REJECTED","retryAfterSeconds":null}""";

	@Autowired
	MockMvc mockMvc;

	@MockitoSpyBean
	PasswordHashing hashing;

	@MockitoSpyBean
	SignIn signIn;

	@Autowired
	AccountRecovery recovery;

	@Autowired
	AccountAdministration administration;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	RecoveryCodeRepository recoveryCodes;

	@Autowired
	JdbcClient jdbc;

	private final ExecutorService pool = Executors.newSingleThreadExecutor();

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from spring_session").update();
		AccountEvents.awaitPasswordChangeHandled(this.jdbc);
		UserTables.deleteAll(this.jdbc);
		this.jdbc.sql("delete from login_failures").update();
	}

	@AfterEach
	void stopPool() {
		this.pool.shutdownNow();
	}

	@Test
	void signInVerifiedBeforeARecoveryResetLeavesNoSession() throws Exception {
		UserId user = account("alice@example.com");
		this.recoveryCodes.replace(user, RecoveryCode.parse(CODE).orElseThrow().digest(), NOW);

		MockHttpServletResponse response = signInAround("alice@example.com",
				() -> assertThat(this.recovery.reset("alice@example.com", CODE, NEW_PASSWORD, Instant.now()))
					.isInstanceOf(RecoveryOutcome.Reset.class));

		assertThat(response.getContentAsString()).isEqualTo(REJECTED);
		assertNoSessionLeft(user, response);
	}

	@Test
	void signInVerifiedBeforeAnInitialPasswordResetLeavesNoSession() throws Exception {
		AccountCreationResult created = this.administration.create("bob@example.com", "밥", PASSWORD);
		UserId user = ((AccountCreationResult.Created) created).user();

		MockHttpServletResponse response = signInAround("bob@example.com",
				() -> assertThat(this.administration.resetInitialPassword("bob@example.com", NEW_PASSWORD))
					.isInstanceOf(InitialPasswordResetResult.Reset.class));

		assertThat(response.getContentAsString()).isEqualTo(REJECTED);
		assertNoSessionLeft(user, response);
	}

	@Test
	void recoveryResetRightAfterTheSignInCheckEndsTheNewSession() throws Exception {
		UserId user = account("carol@example.com");
		this.recoveryCodes.replace(user, RecoveryCode.parse(CODE).orElseThrow().digest(), NOW);
		// 다시 확인이 버전을 본 직후 복구 재설정을 돌린다. spy 가 트랜잭션 프록시의 안쪽이라 재설정은 다시 확인의 트랜잭션(자격 행 잠금을 쥔
		// 채)에 합류하고, 그 트랜잭션은 재설정 뒤에 커밋된다. 지키는 것은 재설정의 세션 지우기 때 로그인 세션이 이미 저장소에 있다는 것이다
		// — 세션 저장 시점을 요청 끝으로 되돌리면 세션이 남는다
		doAnswer(invocation -> {
			Object current = invocation.callRealMethod();
			assertThat(this.recovery.reset("carol@example.com", CODE, NEW_PASSWORD, Instant.now()))
				.isInstanceOf(RecoveryOutcome.Reset.class);
			return current;
		}).when(this.signIn).stillCurrent(any());

		MockHttpServletResponse response = this.mockMvc.perform(signIn("carol@example.com", PASSWORD))
			.andReturn()
			.getResponse();

		assertThat(response.getContentAsString()).as("확인 때는 자격이 그대로였다").isEqualTo("""
				{"result":"SIGNED_IN","retryAfterSeconds":null}""");
		assertNoSessionLeft(user, response);
	}

	/**
	 * 로그인 요청을 보내고, 그 대조가 옛 해시를 읽은 채 기다리는 동안 {@code change} 를 끝까지(커밋) 돌린 뒤 대조를 잇는다.
	 * @return 로그인 응답
	 */
	private MockHttpServletResponse signInAround(String email, Runnable change) throws Exception {
		CountDownLatch verifying = new CountDownLatch(1);
		CountDownLatch changed = new CountDownLatch(1);
		doAnswer(invocation -> {
			verifying.countDown();
			assertThat(changed.await(30, TimeUnit.SECONDS)).isTrue();
			return invocation.callRealMethod();
		}).when(this.hashing).matches(anyString(), anyString());

		Future<MockHttpServletResponse> signIn = this.pool
			.submit(() -> this.mockMvc.perform(signIn(email, PASSWORD)).andReturn().getResponse());
		assertThat(verifying.await(30, TimeUnit.SECONDS)).as("로그인이 대조에 들어갔다").isTrue();
		try {
			change.run();
		}
		finally {
			changed.countDown();
		}
		return signIn.get(30, TimeUnit.SECONDS);
	}

	private void assertNoSessionLeft(UserId user, MockHttpServletResponse response) throws Exception {
		assertThat(sessions(user)).as("옛 비밀번호로 연 세션이 남지 않는다").isZero();
		Cookie session = response.getCookie("SESSION");
		if (session != null && !session.getValue().isEmpty()) {
			this.mockMvc.perform(get("/api/me").cookie(session)).andExpect(content().json("""
					{"result":"ANONYMOUS"}""", JsonCompareMode.LENIENT));
		}
	}

	private UserId account(String email) {
		UserId id = this.accounts.save(Account.create(EmailAddress.parse(email).orElseThrow(), new DisplayName("시험"),
				AccountRole.USER, NOW)).id();
		this.credentials.save(PasswordCredential.of(id, this.hashing.hash(PASSWORD), false, NOW));
		return id;
	}

	private int sessions(UserId user) {
		return this.jdbc.sql("select count(*) from spring_session where principal_name = :name")
			.param("name", Long.toString(user.value()))
			.query(Integer.class)
			.single();
	}

	private static MockHttpServletRequestBuilder signIn(String email, String password) {
		return post("/api/session").cookie(new Cookie("XSRF-TOKEN", CSRF))
			.header("X-XSRF-TOKEN", CSRF)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email":"%s","password":"%s","keep":false}""".formatted(email, password));
	}

}
