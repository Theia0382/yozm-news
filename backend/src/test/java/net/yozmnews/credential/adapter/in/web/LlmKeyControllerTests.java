package net.yozmnews.credential.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.ScriptedProviderClients;
import net.yozmnews.user.SignedInUsers;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 키 처리기 — 앱 전체를 실제 PostgreSQL 로 띄우고 공급자 포트만 키마다 답을 정한 대역으로 바꿔, 등록 · 교체 · 다시 검증 · 삭제 · 목록의 응답과
 * 저장된 행을 손으로 적은 값과 견준다. 사용자는 로그인 API 로 만든 세션이다. 행은 커밋되고, 시험마다 다른 이메일의 사용자를 쓴다.
 * <p>
 * 지키는 규칙: credential-R9 · credential-R10 · credential-R11 · credential-R12 · credential-R16 · credential-R17 · credential-R19
 */
@Import({ TestcontainersConfiguration.class, ScriptedProviderClients.Config.class })
@SpringBootTest
@AutoConfigureMockMvc
class LlmKeyControllerTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	ScriptedProviderClients provider;

	@Autowired
	JdbcClient jdbc;

	private SignedInUsers users;

	@BeforeEach
	void users() {
		this.users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
	}

	@Test
	void listsEveryProviderInOrderWithNoKeyAtFirst() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-empty@example.com");

		this.mockMvc.perform(get("/api/llm-keys").with(user)).andExpect(status().isOk()).andExpect(content().json("""
				{"providers":[{"provider":"OPENAI","key":null},{"provider":"ANTHROPIC","key":null}]}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void registersAValidKeyAndAnswersWithTheHintOnly() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-register@example.com");
		String key = "sk-register-0123456789abWXYZ";

		String body = this.mockMvc.perform(register(user, "ANTHROPIC", key))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("REGISTERED"))
			.andExpect(jsonPath("$.failure").value((Object) null))
			.andExpect(jsonPath("$.key.hint").value("WXYZ"))
			.andExpect(jsonPath("$.key.status").value("VALID"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).doesNotContain(key);
		assertThat(rows(user)).containsExactly("anthropic/VALID/WXYZ/1");
		assertThat(HexFormat.of().formatHex(storedBytes(user, "ciphertext")))
			.doesNotContain(HexFormat.of().formatHex(key.getBytes(StandardCharsets.UTF_8)));
		this.mockMvc.perform(get("/api/llm-keys").with(user))
			.andExpect(jsonPath("$.providers[0].provider").value("OPENAI"))
			.andExpect(jsonPath("$.providers[0].key").value((Object) null))
			.andExpect(jsonPath("$.providers[1].provider").value("ANTHROPIC"))
			.andExpect(jsonPath("$.providers[1].key.hint").value("WXYZ"))
			.andExpect(jsonPath("$.providers[1].key.status").value("VALID"));
		this.mockMvc.perform(get("/api/me").with(user)).andExpect(jsonPath("$.llmKeyRequired").value(false));
	}

	@Test
	void givesNoHintForAKeyShorterThanTwentyCharacters() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-short@example.com");

		// 19자
		this.mockMvc.perform(register(user, "OPENAI", "sk-short-0123456789"))
			.andExpect(jsonPath("$.result").value("REGISTERED"))
			.andExpect(jsonPath("$.key.hint").value(""));
		assertThat(rows(user)).containsExactly("openai/VALID//1");
	}

	@Test
	void stripsTheWhitespaceAroundTheKey() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-strip@example.com");
		String key = "sk-strip-0123456789abcdPQRS";

		this.mockMvc.perform(register(user, "OPENAI", "  " + key + "\\n"))
			.andExpect(jsonPath("$.result").value("REGISTERED"))
			.andExpect(jsonPath("$.key.hint").value("PQRS"));
		assertThat(this.provider.asked()).contains(key);
	}

	@Test
	void doesNotStoreAKeyTheProviderRejectsWhateverTheKind() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-rejected@example.com");

		for (LlmFailureKind kind : List.of(LlmFailureKind.INVALID_KEY, LlmFailureKind.PERMISSION_DENIED,
				LlmFailureKind.QUOTA_EXHAUSTED, LlmFailureKind.TRANSIENT, LlmFailureKind.UNEXPECTED)) {
			String key = "sk-rejected-" + kind + "-0123456789";
			this.provider.fail(key, kind);

			this.mockMvc.perform(register(user, "OPENAI", key)).andExpect(status().isOk()).andExpect(content().json("""
					{"result":"REJECTED","failure":"%s","key":null}
					""".formatted(kind), JsonCompareMode.STRICT));
		}
		assertThat(rows(user)).isEmpty();
		this.mockMvc.perform(get("/api/me").with(user)).andExpect(jsonPath("$.llmKeyRequired").value(true));
	}

	@Test
	void rejectsAKeyThatCannotGoInAHeaderWithoutAskingTheProvider() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-malformed@example.com");

		this.mockMvc.perform(register(user, "OPENAI", "sk-malformed with-space-0123456789"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"REJECTED","failure":"INVALID_KEY","key":null}
					""", JsonCompareMode.STRICT));
		assertThat(this.provider.asked()).doesNotContain("sk-malformed with-space-0123456789");
		assertThat(rows(user)).isEmpty();
	}

	@Test
	void aBlankKeyAMissingKeyAndAnUnknownProviderAreBadRequests() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-bad@example.com");

		this.mockMvc.perform(register(user, "OPENAI", "   ")).andExpect(status().isBadRequest());
		this.mockMvc
			.perform(put("/api/llm-keys/OPENAI").with(user).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(register(user, "openai", "sk-lowercase-0123456789abcd")).andExpect(status().isBadRequest());
		this.mockMvc.perform(register(user, "GEMINI", "sk-unknown-0123456789abcdef")).andExpect(status().isBadRequest());
		assertThat(rows(user)).isEmpty();
	}

	@Test
	void aFailedReplacementKeepsTheOldKeyAndASuccessfulOneTakesItsPlace() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-replace@example.com");
		this.mockMvc.perform(register(user, "OPENAI", "sk-replace-old-0123456789AAAA"))
			.andExpect(jsonPath("$.result").value("REGISTERED"));
		long oldId = keyId(user);
		this.provider.fail("sk-replace-bad-0123456789BBBB", LlmFailureKind.INVALID_KEY);

		this.mockMvc.perform(register(user, "OPENAI", "sk-replace-bad-0123456789BBBB"))
			.andExpect(jsonPath("$.result").value("REJECTED"));

		assertThat(rows(user)).containsExactly("openai/VALID/AAAA/1");
		assertThat(keyId(user)).isEqualTo(oldId);

		this.mockMvc.perform(register(user, "OPENAI", "sk-replace-new-0123456789CCCC"))
			.andExpect(jsonPath("$.result").value("REGISTERED"))
			.andExpect(jsonPath("$.key.hint").value("CCCC"));

		assertThat(rows(user)).containsExactly("openai/VALID/CCCC/1");
		assertThat(keyId(user)).isNotEqualTo(oldId);
	}

	@Test
	void keepsAKeyPerProvider() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-both@example.com");

		this.mockMvc.perform(register(user, "ANTHROPIC", "sk-both-anthropic-0123456789AAAA"));
		this.mockMvc.perform(register(user, "OPENAI", "sk-both-openai-0123456789BBBB"));

		this.mockMvc.perform(get("/api/llm-keys").with(user))
			.andExpect(jsonPath("$.providers[0].provider").value("OPENAI"))
			.andExpect(jsonPath("$.providers[0].key.hint").value("BBBB"))
			.andExpect(jsonPath("$.providers[1].provider").value("ANTHROPIC"))
			.andExpect(jsonPath("$.providers[1].key.hint").value("AAAA"));
	}

	@Test
	void checkingAKeyThatIsNotStoredIsMissing() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-missing@example.com");

		this.mockMvc.perform(check(user, "OPENAI")).andExpect(status().isOk()).andExpect(content().json("""
				{"result":"MISSING","failure":null,"key":null}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void checkingAStoredKeyAsksTheProviderWithThatKeyAndMovesTheValidationTime() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-check-valid@example.com");
		String key = "sk-check-valid-0123456789AAAA";
		this.mockMvc.perform(register(user, "OPENAI", key));
		Instant registered = validatedAt(user);
		int askedBefore = this.provider.asked().size();

		this.mockMvc.perform(check(user, "OPENAI"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("VALID"))
			.andExpect(jsonPath("$.failure").value((Object) null))
			.andExpect(jsonPath("$.key.hint").value("AAAA"))
			.andExpect(jsonPath("$.key.status").value("VALID"));

		// 사용자가 키를 다시 넣지 않았다 — 저장한 키를 열어 물었다
		assertThat(this.provider.asked().subList(askedBefore, this.provider.asked().size())).containsExactly(key);
		assertThat(validatedAt(user)).isAfterOrEqualTo(registered);
		assertThat(rows(user)).containsExactly("openai/VALID/AAAA/1");
	}

	@Test
	void anInvalidOrDeniedAnswerInvalidatesTheKeyAndALaterSuccessBringsItBack() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-check-invalid@example.com");
		String key = "sk-check-invalid-0123456789AAAA";
		this.mockMvc.perform(register(user, "OPENAI", key));
		Instant registered = validatedAt(user);

		this.provider.fail(key, LlmFailureKind.INVALID_KEY);
		this.mockMvc.perform(check(user, "OPENAI"))
			.andExpect(jsonPath("$.result").value("INVALID"))
			.andExpect(jsonPath("$.failure").value("INVALID_KEY"))
			.andExpect(jsonPath("$.key.status").value("INVALID"));
		assertThat(rows(user)).containsExactly("openai/INVALID/AAAA/1");
		assertThat(validatedAt(user)).isEqualTo(registered);
		// 무효가 된 키도 키다 — 화면 이동의 기준은 그대로다
		this.mockMvc.perform(get("/api/me").with(user)).andExpect(jsonPath("$.llmKeyRequired").value(false));

		this.provider.fail(key, LlmFailureKind.PERMISSION_DENIED);
		this.mockMvc.perform(check(user, "OPENAI"))
			.andExpect(jsonPath("$.result").value("INVALID"))
			.andExpect(jsonPath("$.failure").value("PERMISSION_DENIED"));

		this.provider.answer(key, new net.yozmnews.llm.domain.provider.ProbeResult.Reachable());
		this.mockMvc.perform(check(user, "OPENAI"))
			.andExpect(jsonPath("$.result").value("VALID"))
			.andExpect(jsonPath("$.key.status").value("VALID"));
		assertThat(rows(user)).containsExactly("openai/VALID/AAAA/1");
	}

	@Test
	void aQuotaTransientOrUnexpectedAnswerIsUndeterminedAndLeavesTheStatus() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-check-undetermined@example.com");
		String key = "sk-check-undetermined-0123456789AAAA";
		this.mockMvc.perform(register(user, "OPENAI", key));

		for (LlmFailureKind kind : List.of(LlmFailureKind.QUOTA_EXHAUSTED, LlmFailureKind.TRANSIENT,
				LlmFailureKind.UNEXPECTED)) {
			this.provider.fail(key, kind);
			this.mockMvc.perform(check(user, "OPENAI"))
				.andExpect(jsonPath("$.result").value("UNDETERMINED"))
				.andExpect(jsonPath("$.failure").value(kind.name()))
				.andExpect(jsonPath("$.key.status").value("VALID"));
		}
		assertThat(rows(user)).containsExactly("openai/VALID/AAAA/1");
	}

	@Test
	void aKeyRemovedWhileBeingCheckedIsMissing() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-check-removed@example.com");
		String key = "sk-check-removed-0123456789AAAA";
		this.mockMvc.perform(register(user, "OPENAI", key));
		// 공급자가 답하기 전에 그 키가 지워진다
		this.provider.before(key,
				() -> this.jdbc.sql("delete from llm_credentials where user_id = :id")
					.param("id", user.id().value())
					.update(),
				new net.yozmnews.llm.domain.provider.ProbeResult.Failed(LlmFailureKind.INVALID_KEY, "시험 문구"));

		this.mockMvc.perform(check(user, "OPENAI")).andExpect(content().json("""
				{"result":"MISSING","failure":null,"key":null}
				""", JsonCompareMode.STRICT));
		assertThat(rows(user)).isEmpty();
	}

	@Test
	void aKeyReplacedWhileBeingCheckedKeepsItsOwnStatus() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-check-replaced@example.com");
		String oldKey = "sk-check-replaced-old-0123456789AAAA";
		this.mockMvc.perform(register(user, "OPENAI", oldKey));
		// 공급자가 옛 키를 무효로 답하기 전에 새 키로 바뀐다
		this.provider.before(oldKey, () -> {
			try {
				this.mockMvc.perform(register(user, "OPENAI", "sk-check-replaced-new-0123456789BBBB"));
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}, new net.yozmnews.llm.domain.provider.ProbeResult.Failed(LlmFailureKind.INVALID_KEY, "시험 문구"));

		// 결과는 옛 키의 것이고 키는 새 키의 지금 상태다
		this.mockMvc.perform(check(user, "OPENAI"))
			.andExpect(jsonPath("$.result").value("INVALID"))
			.andExpect(jsonPath("$.failure").value("INVALID_KEY"))
			.andExpect(jsonPath("$.key.hint").value("BBBB"))
			.andExpect(jsonPath("$.key.status").value("VALID"));
		assertThat(rows(user)).containsExactly("openai/VALID/BBBB/1");
	}

	@Test
	void removesTheRowAtOnceAndRemovingAgainIsTheSame() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("keys-remove@example.com");
		this.mockMvc.perform(register(user, "OPENAI", "sk-remove-openai-0123456789AAAA"));
		this.mockMvc.perform(register(user, "ANTHROPIC", "sk-remove-anthropic-0123456789BBBB"));

		this.mockMvc.perform(delete("/api/llm-keys/OPENAI").with(user)).andExpect(status().isNoContent());

		assertThat(rows(user)).containsExactly("anthropic/VALID/BBBB/1");
		this.mockMvc.perform(delete("/api/llm-keys/OPENAI").with(user)).andExpect(status().isNoContent());
		this.mockMvc.perform(delete("/api/llm-keys/ANTHROPIC").with(user)).andExpect(status().isNoContent());
		assertThat(rows(user)).isEmpty();
		this.mockMvc.perform(get("/api/me").with(user)).andExpect(jsonPath("$.llmKeyRequired").value(true));
	}

	private static MockHttpServletRequestBuilder register(SignedInUsers.SignedInUser user, String provider,
			String key) {
		return put("/api/llm-keys/" + provider).with(user).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"%s"}""".formatted(key));
	}

	private static MockHttpServletRequestBuilder check(SignedInUsers.SignedInUser user, String provider) {
		return post("/api/llm-keys/" + provider + "/checks").with(user);
	}

	/** 그 사용자의 행 — 공급자 / 상태 / 힌트 / KEK 버전, 공급자 순 */
	private List<String> rows(SignedInUsers.SignedInUser user) {
		return this.jdbc
			.sql("""
					select provider || '/' || status || '/' || key_hint || '/' || kek_version from llm_credentials
					where user_id = :id order by provider
					""")
			.param("id", user.id().value())
			.query(String.class)
			.list();
	}

	private long keyId(SignedInUsers.SignedInUser user) {
		return this.jdbc.sql("select id from llm_credentials where user_id = :id")
			.param("id", user.id().value())
			.query(Long.class)
			.single();
	}

	private Instant validatedAt(SignedInUsers.SignedInUser user) {
		return this.jdbc.sql("select validated_at from llm_credentials where user_id = :id")
			.param("id", user.id().value())
			.query((rs, row) -> rs.getTimestamp(1).toInstant())
			.single();
	}

	private byte[] storedBytes(SignedInUsers.SignedInUser user, String column) {
		return this.jdbc.sql("select " + column + " from llm_credentials where user_id = :id")
			.param("id", user.id().value())
			.query((rs, row) -> rs.getBytes(1))
			.single();
	}

}
