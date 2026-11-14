package net.yozmnews.llm.adapter.out.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.LlmProviderClients;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.platform.SecretValue;
import org.junit.jupiter.api.Test;

/**
 * 공급자 클라이언트의 로컬 대체물 — 가짜 키는 접두 뒤의 낱말이 결과를 정하고, 그 밖의 키는 넘겨받은 구현으로 간다.
 * <p>
 * 지키는 규칙: llm-R16
 */
class LocalProviderClientsTests {

	private final RecordingClients delegate = new RecordingClients();

	private final LocalProviderClients clients = new LocalProviderClients(this.delegate);

	@Test
	void thePrefixDoesNotCollideWithRealKeyPrefixes() {
		assertThat(LocalProviderClients.PREFIX).isEqualTo("yozm-local-").doesNotStartWith("sk-");
	}

	@Test
	void theWordAfterThePrefixDecidesTheResult() {
		assertThat(probe("yozm-local-valid")).isEqualTo(new ProbeResult.Reachable());
		assertThat(probe("yozm-local-invalid"))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.INVALID_KEY, "로컬 대체물이 정한 결과 — INVALID_KEY"));
		assertThat(probe("yozm-local-denied")).isEqualTo(
				new ProbeResult.Failed(LlmFailureKind.PERMISSION_DENIED, "로컬 대체물이 정한 결과 — PERMISSION_DENIED"));
		assertThat(probe("yozm-local-quota"))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.QUOTA_EXHAUSTED, "로컬 대체물이 정한 결과 — QUOTA_EXHAUSTED"));
		assertThat(probe("yozm-local-transient"))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.TRANSIENT, "로컬 대체물이 정한 결과 — TRANSIENT"));
		assertThat(probe("yozm-local-unexpected"))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.UNEXPECTED, "로컬 대체물이 정한 결과 — UNEXPECTED"));
		assertThat(this.delegate.opened).isEmpty();
	}

	@Test
	void theWordEndsAtTheNextHyphen() {
		assertThat(probe("yozm-local-valid-abcd1234")).isEqualTo(new ProbeResult.Reachable());
		assertThat(probe("yozm-local-quota-abcd1234"))
			.isEqualTo(new ProbeResult.Failed(LlmFailureKind.QUOTA_EXHAUSTED, "로컬 대체물이 정한 결과 — QUOTA_EXHAUSTED"));
	}

	@Test
	void anUnknownWordIsAnInvalidKey() {
		ProbeResult invalid = new ProbeResult.Failed(LlmFailureKind.INVALID_KEY, "로컬 대체물이 정한 결과 — INVALID_KEY");

		assertThat(probe("yozm-local-")).isEqualTo(invalid);
		assertThat(probe("yozm-local-vaild")).isEqualTo(invalid);
		assertThat(probe("yozm-local-VALID")).isEqualTo(invalid);
		assertThat(probe("yozm-local-validity")).isEqualTo(invalid);
		assertThat(this.delegate.opened).isEmpty();
	}

	@Test
	void aKeyWithoutThePrefixGoesToTheDelegate() {
		SecretValue real = SecretValue.of("sk-ant-real-key-0123");
		SecretValue almost = SecretValue.of("yozm-localvalid");

		LlmProviderClient client = this.clients.open(Provider.ANTHROPIC, real);
		this.clients.open(Provider.OPENAI, almost);

		assertThat(client).isSameAs(this.delegate.client);
		assertThat(this.delegate.opened).containsExactly(Provider.ANTHROPIC, Provider.OPENAI);
		assertThat(this.delegate.keys).containsExactly(real, almost);
	}

	@Test
	void theSubstituteAnswersForBothProviders() {
		try (LlmProviderClient client = this.clients.open(Provider.ANTHROPIC, SecretValue.of("yozm-local-denied"))) {
			assertThat(client.probe()).isEqualTo(
					new ProbeResult.Failed(LlmFailureKind.PERMISSION_DENIED, "로컬 대체물이 정한 결과 — PERMISSION_DENIED"));
		}
	}

	private ProbeResult probe(String key) {
		try (LlmProviderClient client = this.clients.open(Provider.OPENAI, SecretValue.of(key))) {
			return client.probe();
		}
	}

	/** 받은 공급자 · 키를 적고 같은 클라이언트를 돌려주는 가짜 위임 대상 */
	private static final class RecordingClients implements LlmProviderClients {

		private final List<Provider> opened = new ArrayList<>();

		private final List<SecretValue> keys = new ArrayList<>();

		private final LlmProviderClient client = new LlmProviderClient() {

			@Override
			public ProbeResult probe() {
				return new ProbeResult.Reachable();
			}

			@Override
			public void close() {
			}

		};

		@Override
		public LlmProviderClient open(Provider provider, SecretValue key) {
			this.opened.add(provider);
			this.keys.add(key);
			return this.client;
		}

	}

}
