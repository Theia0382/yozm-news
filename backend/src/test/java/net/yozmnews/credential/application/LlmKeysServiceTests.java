package net.yozmnews.credential.application;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.credential.domain.key.LlmKeyRepository;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 키 유무와 키가 있는 공급자 — 실제 PostgreSQL 의 키 행으로 공급자(상태 무관)와 쓸 수 있는 공급자(`VALID`)를 본다. 시험마다 트랜잭션이
 * 되돌려진다.
 * <p>
 * 지키는 규칙: credential-R18
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class LlmKeysServiceTests {

	@Autowired
	LlmKeyRepository keys;

	@Autowired
	JdbcClient jdbc;

	private LlmKeysService service;

	@BeforeEach
	void wire() {
		this.service = new LlmKeysService(this.keys);
	}

	@Test
	void bothProvidersInDeclarationOrderButOnlyTheValidOneIsUsable() {
		key(7, "anthropic", "VALID");
		key(7, "openai", "INVALID");
		key(8, "openai", "VALID");

		assertThat(this.service.present(new UserId(7))).isTrue();
		assertThat(this.service.providers(new UserId(7))).containsExactly(Provider.OPENAI, Provider.ANTHROPIC);
		assertThat(this.service.usable(new UserId(7))).containsExactly(Provider.ANTHROPIC);
	}

	@Test
	void anInvalidKeyIsAProviderButNotUsable() {
		key(7, "openai", "INVALID");

		assertThat(this.service.providers(new UserId(7))).containsExactly(Provider.OPENAI);
		assertThat(this.service.usable(new UserId(7))).isEmpty();
	}

	@Test
	void aUserWithoutKeysHasNoProviders() {
		key(8, "openai", "VALID");

		assertThat(this.service.present(new UserId(7))).isFalse();
		assertThat(this.service.providers(new UserId(7))).isEmpty();
		assertThat(this.service.usable(new UserId(7))).isEmpty();
	}

	private void key(long user, String provider, String status) {
		this.jdbc.sql("""
				insert into llm_credentials (user_id, provider, ciphertext, encrypted_dek, kek_version, key_hint, status,
				  validated_at, created_at)
				values (:user, :provider, '\\x01'::bytea, '\\x01'::bytea, 1, 'WXYZ', :status, now(), now())
				""").param("user", user).param("provider", provider).param("status", status).update();
	}

}
