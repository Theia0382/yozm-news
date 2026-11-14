package net.yozmnews.credential.domain.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.credential.domain.crypto.KeyOwner;
import net.yozmnews.credential.domain.crypto.SealedKey;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 키 aggregate 의 저장 — 실제 PostgreSQL 에서 키 행이 저장 · 읽기를 오가고, 주인마다 하나만 저장되며, 상태 · KEK 버전의 조회가 맞는지 본다.
 * 시험마다 트랜잭션이 되돌려진다.
 * <p>
 * 지키는 규칙: credential-R11 · credential-R18
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class LlmKeyRepositoryTests {

	private static final Instant NOW = Instant.parse("2026-11-14T06:00:00Z");

	private static final KeyOwner ALICE_OPENAI = new KeyOwner(new UserId(7), Provider.OPENAI);

	@Autowired
	LlmKeyRepository keys;

	@Autowired
	JdbcClient jdbc;

	@Test
	void hasTheKeyColumnsAndNoPlainKeyColumn() {
		assertThat(this.jdbc.sql("""
				select column_name from information_schema.columns
				where table_schema = 'public' and table_name = 'llm_credentials' order by ordinal_position
				""").query(String.class).list())
			.containsExactly("id", "user_id", "provider", "ciphertext", "encrypted_dek", "kek_version", "key_hint",
					"status", "validated_at", "created_at");
	}

	@Test
	void savesAndReadsAKey() {
		StoredLlmKey saved = this.keys.save(StoredLlmKey.register(ALICE_OPENAI, sealed(1), "WXYZ", NOW));

		StoredLlmKey read = this.keys.findByUserIdAndProvider(7, "openai").orElseThrow();

		assertThat(read.id()).isEqualTo(saved.id());
		assertThat(read.owner()).isEqualTo(ALICE_OPENAI);
		assertThat(read.sealed().ciphertext()).containsExactly(1, 2, 3);
		assertThat(read.sealed().encryptedDek()).containsExactly(4, 5, 6);
		assertThat(read.kekVersion()).isEqualTo(1);
		assertThat(read.hint()).isEqualTo("WXYZ");
		assertThat(read.status()).isEqualTo(KeyStatus.VALID);
		assertThat(read.validatedAt()).isEqualTo(NOW);
		assertThat(read.createdAt()).isEqualTo(NOW);
		assertThat(read.toString()).isEqualTo("StoredLlmKey[id=" + saved.id() + ", provider=openai, status=VALID]");
		assertThat(this.jdbc.sql("select provider, status from llm_credentials where id = :id")
			.param("id", saved.id())
			.query((rs, row) -> rs.getString("provider") + "/" + rs.getString("status"))
			.single()).isEqualTo("openai/VALID");
	}

	@Test
	void allowsOneKeyPerOwner() {
		this.keys.save(StoredLlmKey.register(ALICE_OPENAI, sealed(1), "WXYZ", NOW));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.keys.save(StoredLlmKey.register(ALICE_OPENAI, sealed(1), "abcd", NOW)));
	}

	@Test
	void keepsAKeyPerProviderAndPerUser() {
		this.keys.save(StoredLlmKey.register(ALICE_OPENAI, sealed(1), "WXYZ", NOW));
		this.keys.save(StoredLlmKey.register(new KeyOwner(new UserId(7), Provider.ANTHROPIC), sealed(1), "", NOW));
		this.keys.save(StoredLlmKey.register(new KeyOwner(new UserId(8), Provider.OPENAI), sealed(2), "abcd", NOW));

		assertThat(this.keys.findByUserId(7)).extracting(StoredLlmKey::provider)
			.containsExactlyInAnyOrder(Provider.OPENAI, Provider.ANTHROPIC);
		assertThat(this.keys.existsByUserId(7)).isTrue();
		assertThat(this.keys.existsByUserId(8)).isTrue();
		assertThat(this.keys.existsByUserId(9)).isFalse();
		assertThat(this.keys.findByUserIdAndProvider(8, "anthropic")).isEmpty();
	}

	@Test
	void existsRegardlessOfTheStatus() {
		StoredLlmKey saved = this.keys.save(StoredLlmKey.register(ALICE_OPENAI, sealed(1), "WXYZ", NOW));
		this.keys.save(saved.checked(KeyStatus.INVALID, saved.validatedAt()));

		assertThat(this.keys.existsByUserId(7)).isTrue();
		assertThat(this.keys.findByUserIdAndProvider(7, "openai").orElseThrow().status()).isEqualTo(KeyStatus.INVALID);
	}

	@Test
	void countsKeysPerKekVersionAndFindsThoseOffTheActiveVersion() {
		long first = this.keys.save(StoredLlmKey.register(ALICE_OPENAI, sealed(1), "", NOW)).id();
		long second = this.keys
			.save(StoredLlmKey.register(new KeyOwner(new UserId(8), Provider.OPENAI), sealed(1), "", NOW))
			.id();
		this.keys.save(StoredLlmKey.register(new KeyOwner(new UserId(9), Provider.OPENAI), sealed(3), "", NOW));

		assertThat(this.keys.versionsInUse()).containsExactly(new KekVersionUse(1, 2), new KekVersionUse(3, 1));
		assertThat(this.keys.idsNotOnVersion(3, 100)).containsExactly(first, second);
		assertThat(this.keys.idsNotOnVersion(3, 1)).containsExactly(first);
		assertThat(this.keys.countNotOnVersion(3)).isEqualTo(2);
		assertThat(this.keys.countNotOnVersion(1)).isEqualTo(1);
	}

	@Test
	void locksAKeyByIdAndByOwner() {
		long id = this.keys.save(StoredLlmKey.register(ALICE_OPENAI, sealed(1), "WXYZ", NOW)).id();

		assertThat(this.keys.lockById(id)).map(StoredLlmKey::hint).contains("WXYZ");
		assertThat(this.keys.lockByOwner(7, "openai")).map(StoredLlmKey::id).contains(id);
		assertThat(this.keys.lockById(id + 1)).isEmpty();
		assertThat(this.keys.lockByOwner(7, "anthropic")).isEmpty();
	}

	private static SealedKey sealed(int kekVersion) {
		return new SealedKey(new byte[] { 1, 2, 3 }, new byte[] { 4, 5, 6 }, kekVersion);
	}

}
