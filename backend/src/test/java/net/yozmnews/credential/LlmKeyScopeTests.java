package net.yozmnews.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.llm.ScriptedProviderClients;
import net.yozmnews.user.SignedInUsers;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 키 경로의 사용자 격리 — 사용자 A 가 등록한 키를 B 의 세션으로 볼 수 없고, B 의 다시 검증 · 삭제 · 등록이 A 의 키 행을 건드리지 않는다. 세션
 * 없이 부르면 401 이다(docs/qa/test-strategy.md §13). 공급자 포트는 키마다 답을 정하는 대역이다.
 * <p>
 * 지키는 규칙: user-R6 · credential-R17
 */
@Import({ TestcontainersConfiguration.class, ScriptedProviderClients.Config.class })
@SpringBootTest
@AutoConfigureMockMvc
public class LlmKeyScopeTests {

	private static final String A_KEY = "sk-scope-a-0123456789abAAAA";

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

	@Test
	void userBCannotSeeCheckRemoveOrReplaceUserAsKey() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("key-scope-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("key-scope-b@example.com");
		this.mockMvc
			.perform(put("/api/llm-keys/OPENAI").with(a).contentType(MediaType.APPLICATION_JSON).content("""
					{"key":"%s"}""".formatted(A_KEY)))
			.andExpect(jsonPath("$.result").value("REGISTERED"));
		List<String> aRows = rows(a);
		assertThat(aRows).hasSize(1);

		// B 는 A 의 키를 보지 못한다
		this.mockMvc.perform(get("/api/llm-keys").with(b)).andExpect(status().isOk()).andExpect(content().json("""
				{"providers":[{"provider":"OPENAI","key":null},{"provider":"ANTHROPIC","key":null}]}
				""", JsonCompareMode.STRICT));
		// B 의 다시 검증은 A 의 키로 묻지 않는다
		int asked = this.provider.asked().size();
		this.mockMvc.perform(post("/api/llm-keys/OPENAI/checks").with(b)).andExpect(content().json("""
				{"result":"MISSING","failure":null,"key":null}
				""", JsonCompareMode.STRICT));
		assertThat(this.provider.asked()).hasSize(asked);
		// B 의 삭제 · 등록은 A 의 행을 건드리지 않는다
		this.mockMvc.perform(delete("/api/llm-keys/OPENAI").with(b)).andExpect(status().isNoContent());
		assertThat(rows(a)).isEqualTo(aRows);
		this.mockMvc
			.perform(put("/api/llm-keys/OPENAI").with(b).contentType(MediaType.APPLICATION_JSON).content("""
					{"key":"sk-scope-b-0123456789abBBBB"}"""))
			.andExpect(jsonPath("$.result").value("REGISTERED"));
		assertThat(rows(a)).isEqualTo(aRows);
		assertThat(rows(b)).hasSize(1).doesNotContainAnyElementsOf(aRows);

		// A 는 자기 키만 본다
		this.mockMvc.perform(get("/api/llm-keys").with(a))
			.andExpect(jsonPath("$.providers[0].key.hint").value("AAAA"))
			.andExpect(jsonPath("$.providers[1].key").value((Object) null));
	}

	@Test
	void everyKeyPathNeedsASession() throws Exception {
		this.mockMvc.perform(get("/api/llm-keys")).andExpect(status().isUnauthorized());
		this.mockMvc
			.perform(put("/api/llm-keys/OPENAI").cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "t"))
				.header("X-XSRF-TOKEN", "t")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"sk-scope-anonymous-0123456789"}"""))
			.andExpect(status().isUnauthorized());
		this.mockMvc
			.perform(post("/api/llm-keys/OPENAI/checks").cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "t"))
				.header("X-XSRF-TOKEN", "t"))
			.andExpect(status().isUnauthorized());
		this.mockMvc
			.perform(delete("/api/llm-keys/OPENAI").cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "t"))
				.header("X-XSRF-TOKEN", "t"))
			.andExpect(status().isUnauthorized());
		assertThat(this.provider.asked()).doesNotContain("sk-scope-anonymous-0123456789");
	}

	/** 그 사용자의 행 — ID / 공급자 / 상태 / 힌트 */
	private List<String> rows(SignedInUsers.SignedInUser user) {
		return this.jdbc
			.sql("""
					select id || '/' || provider || '/' || status || '/' || key_hint from llm_credentials
					where user_id = :id order by provider
					""")
			.param("id", user.id().value())
			.query(String.class)
			.list();
	}

}
