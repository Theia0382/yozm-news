package net.yozmnews.credential.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.ScriptedProviderClients;
import net.yozmnews.user.SignedInUsers;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 키 경로의 응답과 로그에 키가 남지 않음 — 앱 전체를 띄워 등록(성공 · 실패 종류마다 · 예상 밖 예외 · 깨진 본문 · 빈 키) · 다시 검증 · 목록 ·
 * 삭제를 부르고, 모은 응답 본문과 캡처한 출력에 키와 그 끝 8자가 없음을 본다. 공급자 포트는 키마다 답을 정하는 대역이다.
 * <p>
 * 지키는 규칙: credential-R19 · credential-R4
 */
@Import({ TestcontainersConfiguration.class, ScriptedProviderClients.Config.class })
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LlmKeyLeakTests {

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

	@Test
	void responsesAndLogsOfEveryKeyPathCarryNoKey(CapturedOutput output) throws Exception {
		SignedInUsers.SignedInUser user = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing)
			.signIn("key-leak@example.com");
		List<String> keys = new ArrayList<>();
		List<String> bodies = new ArrayList<>();

		// 등록 — 실패 종류마다
		for (LlmFailureKind kind : LlmFailureKind.values()) {
			String key = "sk-leak-" + kind + "-0123456789-" + kind.ordinal() + "TAILTAIL";
			keys.add(key);
			this.provider.fail(key, kind);
			bodies.add(body(register(user, key)));
		}
		// 등록 — 확인 요청이 키가 든 문구의 예외를 던진다
		String thrown = "sk-leak-thrown-0123456789-THROWNEND";
		keys.add(thrown);
		this.provider.raise(thrown, () -> new IllegalStateException("Incorrect API key provided: " + thrown));
		bodies.add(body(register(user, thrown)));
		// 등록 — 깨진 본문과 형이 다른 값, 헤더에 실을 수 없는 키
		String broken = "sk-leak-broken-0123456789-BROKENEND";
		keys.add(broken);
		bodies.add(body(put("/api/llm-keys/OPENAI").with(user)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"key\": " + broken + "}")));
		bodies.add(body(put("/api/llm-keys/OPENAI").with(user)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"key\": [\"" + broken + "\"]}")));
		String malformed = "sk-leak malformed-0123456789-SPACEEND";
		keys.add(malformed);
		bodies.add(body(register(user, malformed)));
		bodies.add(body(register(user, "   ")));
		// 등록 성공 → 목록 → 다시 검증(성공 · 무효 · 예상 밖 예외) → 삭제
		String stored = "sk-leak-stored-0123456789-STOREDEND";
		keys.add(stored);
		bodies.add(body(register(user, stored)));
		bodies.add(body(get("/api/llm-keys").with(user)));
		bodies.add(body(post("/api/llm-keys/OPENAI/checks").with(user)));
		this.provider.fail(stored, LlmFailureKind.INVALID_KEY);
		bodies.add(body(post("/api/llm-keys/OPENAI/checks").with(user)));
		this.provider.raise(stored, () -> new IllegalArgumentException("bad key " + stored));
		bodies.add(body(post("/api/llm-keys/OPENAI/checks").with(user)));
		bodies.add(body(delete("/api/llm-keys/OPENAI").with(user)));

		// 대조 — 요청이 실제로 처리됐고 경고 로그가 캡처됐다. 아래의 부정 단언이 빈 응답 · 빈 출력 때문에 통과하는 것이 아니다
		assertThat(bodies).hasSize(16);
		assertThat(bodies.get(0)).isEqualTo("""
				{"result":"REJECTED","failure":"INVALID_KEY","key":null}""");
		assertThat(bodies.get(10)).contains("\"result\":\"REGISTERED\"").contains("\"hint\":\"DEND\"");
		assertThat(bodies.get(14)).contains("\"result\":\"UNDETERMINED\"").contains("\"failure\":\"UNEXPECTED\"");
		assertThat(output.getAll()).contains("LLM 키 확인 중 예상 못한 오류 — IllegalStateException")
			.contains("LLM 키 확인 중 예상 못한 오류 — IllegalArgumentException");

		String responses = String.join("\n", bodies);
		for (String key : keys) {
			String tail = key.substring(key.length() - 8);
			assertThat(responses).as("응답").doesNotContain(key).doesNotContain(tail);
			assertThat(output.getAll()).as("로그").doesNotContain(key).doesNotContain(tail);
		}
		assertThat(output.getAll()).doesNotContain("Incorrect API key provided", "bad key sk-");
	}

	private static MockHttpServletRequestBuilder register(SignedInUsers.SignedInUser user, String key) {
		return put("/api/llm-keys/OPENAI").with(user).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"%s"}""".formatted(key));
	}

	private String body(MockHttpServletRequestBuilder request) throws Exception {
		return this.mockMvc.perform(request).andReturn().getResponse().getContentAsString();
	}

}
