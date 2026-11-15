package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.credential.CredentialInvalidated;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ScriptedProviderClients;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.SignedInUsers;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.domain.notice.KeyInvalidatedContent;
import net.yozmnews.user.domain.notice.Notice;
import net.yozmnews.user.domain.notice.NoticeContent;
import net.yozmnews.user.domain.notice.NoticeKind;
import net.yozmnews.user.domain.notice.NoticeRepository;
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
 * 키 무효 알림 — 앱 전체를 실제 PostgreSQL 로 띄워, 무효 이벤트 하나가 그 사용자의 알림 하나가 되고 같은 이벤트를 거듭 받아도 하나임을 보고,
 * 화면의 다시 검증에서 리스너를 지나 알림함까지 이어지는 실제 경로를 본다. 공급자 포트만 키마다 답을 정하는 대역이다. 리스너는 커밋 뒤에
 * 비동기로 돌아 알림을 기다린다.
 * <p>
 * 지키는 규칙: user-R36 · credential-R15
 */
@Import({ TestcontainersConfiguration.class, ScriptedProviderClients.Config.class })
@SpringBootTest
@AutoConfigureMockMvc
class KeyInvalidatedNoticesTests {

	private static final Instant INVALIDATED = Instant.parse("2026-11-14T07:30:00.123456Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	KeyInvalidatedNotices keyNotices;

	@Autowired
	NoticeRepository notices;

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
	void oneEventLeavesOneNoticeForThatUserOnly() throws Exception {
		UserId alice = this.users.signIn("key-notice-alice@example.com").id();
		UserId bob = this.users.signIn("key-notice-bob@example.com").id();

		this.keyNotices.record(new CredentialInvalidated(alice, Provider.ANTHROPIC, 41, INVALIDATED));

		assertThat(contents(alice)).containsExactly(new KeyInvalidatedContent(Provider.ANTHROPIC, 41, INVALIDATED));
		assertThat(this.notices.page(alice, null, 50)).extracting(Notice::kind, Notice::revision, Notice::read)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(NoticeKind.KEY_INVALIDATED, 1, false));
		assertThat(contents(bob)).isEmpty();
	}

	@Test
	void theSameEventReceivedTwiceLeavesOneNoticeAndAnotherTransitionLeavesAnother() throws Exception {
		UserId alice = this.users.signIn("key-notice-twice@example.com").id();
		CredentialInvalidated event = new CredentialInvalidated(alice, Provider.OPENAI, 41, INVALIDATED);

		this.keyNotices.record(event);
		this.keyNotices.record(event);

		assertThat(contents(alice)).hasSize(1);

		// 같은 키가 유효로 돌아왔다가 다시 무효가 됐다 — 무효 시각이 다른 전이다
		this.keyNotices.record(new CredentialInvalidated(alice, Provider.OPENAI, 41, INVALIDATED.plusSeconds(60)));

		assertThat(contents(alice)).containsExactly(
				new KeyInvalidatedContent(Provider.OPENAI, 41, INVALIDATED.plusSeconds(60)),
				new KeyInvalidatedContent(Provider.OPENAI, 41, INVALIDATED));
	}

	@Test
	void aCheckThatInvalidatesTheKeyReachesTheNoticeInboxThroughTheListener() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("key-notice-flow@example.com");
		String key = "sk-notice-flow-0123456789abAAAA";
		this.mockMvc
			.perform(put("/api/llm-keys/ANTHROPIC").with(user).contentType(MediaType.APPLICATION_JSON).content("""
					{"key":"%s"}""".formatted(key)))
			.andExpect(jsonPath("$.result").value("REGISTERED"));
		long keyId = this.jdbc.sql("select id from llm_credentials where user_id = :id")
			.param("id", user.id().value())
			.query(Long.class)
			.single();
		// 대조 — 등록과 성공한 다시 검증은 알림을 남기지 않는다
		this.mockMvc.perform(post("/api/llm-keys/ANTHROPIC/checks").with(user))
			.andExpect(jsonPath("$.result").value("VALID"));

		this.provider.fail(key, LlmFailureKind.INVALID_KEY);
		this.mockMvc.perform(post("/api/llm-keys/ANTHROPIC/checks").with(user))
			.andExpect(jsonPath("$.result").value("INVALID"));

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(contents(user.id())).hasSize(1));
		KeyInvalidatedContent content = (KeyInvalidatedContent) contents(user.id()).getFirst();
		assertThat(content.provider()).isEqualTo(Provider.ANTHROPIC);
		assertThat(content.keyId()).isEqualTo(keyId);
		this.mockMvc.perform(get("/api/notices").with(user))
			.andExpect(jsonPath("$.notices.length()").value(1))
			.andExpect(jsonPath("$.notices[0].kind").value("KEY_INVALIDATED"))
			.andExpect(jsonPath("$.notices[0].keyInvalidated.provider").value("ANTHROPIC"))
			.andExpect(jsonPath("$.notices[0].keyInvalidated.invalidatedAt").value(content.invalidatedAt().toString()));

		// 이미 무효인 키의 거듭된 실패는 알림을 더하지 않고, 유효로 돌아왔다가 다시 무효가 되면 하나 더 남는다
		this.mockMvc.perform(post("/api/llm-keys/ANTHROPIC/checks").with(user))
			.andExpect(jsonPath("$.result").value("INVALID"));
		this.provider.answer(key, new ProbeResult.Reachable());
		this.mockMvc.perform(post("/api/llm-keys/ANTHROPIC/checks").with(user))
			.andExpect(jsonPath("$.result").value("VALID"));
		this.provider.fail(key, LlmFailureKind.PERMISSION_DENIED);
		this.mockMvc.perform(post("/api/llm-keys/ANTHROPIC/checks").with(user))
			.andExpect(jsonPath("$.result").value("INVALID"));

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(contents(user.id())).hasSize(2));
		// 리스너가 더 돌 것이 없을 때까지 기다린 뒤에도 둘이다
		await().during(Duration.ofSeconds(2))
			.atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(contents(user.id())).hasSize(2));
	}

	private List<NoticeContent> contents(UserId user) {
		return this.notices.page(user, null, 50).stream().map(Notice::content).toList();
	}

}
