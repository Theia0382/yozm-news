package net.yozmnews.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.yozmnews.credential.domain.crypto.EnvelopeCipher;
import net.yozmnews.credential.domain.crypto.KeyEncryptionKeys;
import net.yozmnews.credential.domain.key.KekVersionUse;
import net.yozmnews.credential.domain.key.LlmKeyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

/**
 * KEK 의 기동 적재와 검사 — 손으로 쓴 파일로, 설정이 없거나 파일이 없거나 형식이 틀리거나 저장된 키의 버전이 빠지면 멈추고 문구가 설정 이름 ·
 * 경로 · 줄 번호 · 버전과 키의 수를 말하며 값은 담지 않음을 본다. 판정은 스프링 컨텍스트 없이 보고, 그 판정이 기동을 막는 결선은 이
 * 구성만 올린 컨텍스트로 본다(저장소는 버전마다의 키 수만 답하는 대역).
 * <p>
 * 지키는 규칙: credential-R5 · credential-R6 · credential-R8
 */
class KekConfigurationTests {

	/** 0x01 로 채운 32바이트의 Base64 */
	private static final String ONES = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";

	/** 0x02 로 채운 32바이트의 Base64 */
	private static final String TWOS = "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=";

	private final ResourceLoader resources = new DefaultResourceLoader();

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(KekConfiguration.class);

	@Test
	void readsTheFileTheSettingPointsTo(@TempDir Path dir) throws IOException {
		Path file = Files.writeString(dir.resolve("kek"), "1:" + ONES + "\n2:" + TWOS + "\n");

		KeyEncryptionKeys keys = KekConfiguration.load(file.toString(), this.resources);

		assertThat(keys.versions()).containsExactly(1, 2);
		assertThat(keys.activeVersion()).isEqualTo(2);
	}

	@Test
	void readsAClasspathLocation() {
		assertThat(KekConfiguration.load("classpath:credential/test-kek", this.resources).versions())
			.containsExactly(1);
	}

	@Test
	void doesNotStartWithoutTheSetting() {
		String message = "yozmnews.credential.kek-file(환경 변수 YOZMNEWS_CREDENTIAL_KEK_FILE) 가 없다"
				+ " — LLM 키 암호화 키(KEK) 파일의 경로를 적는다";
		assertThatIllegalStateException().isThrownBy(() -> KekConfiguration.load(null, this.resources))
			.withMessage(message);
		assertThatIllegalStateException().isThrownBy(() -> KekConfiguration.load("", this.resources))
			.withMessage(message);
		assertThatIllegalStateException().isThrownBy(() -> KekConfiguration.load("  ", this.resources))
			.withMessage(message);
	}

	@Test
	void doesNotStartWhenTheFileIsMissing(@TempDir Path dir) {
		Path missing = dir.resolve("kek");

		assertThatIllegalStateException().isThrownBy(() -> KekConfiguration.load(missing.toString(), this.resources))
			.withMessage("yozmnews.credential.kek-file(환경 변수 YOZMNEWS_CREDENTIAL_KEK_FILE) 가 가리키는 KEK 파일(" + missing
					+ ")이 없거나 읽을 수 없다");
		assertThatIllegalStateException()
			.isThrownBy(() -> KekConfiguration.load("classpath:credential/no-such-kek", this.resources))
			.withMessageContaining("classpath:credential/no-such-kek")
			.withMessageEndingWith("이 없거나 읽을 수 없다");
	}

	@Test
	void doesNotStartWhenTheFormatIsWrongAndNamesTheLinesWithoutValues(@TempDir Path dir) throws IOException {
		Path file = Files.writeString(dir.resolve("kek"), "1:" + ONES + "\n1:" + TWOS + "\n2:AQEB\n");

		assertThatIllegalStateException().isThrownBy(() -> KekConfiguration.load(file.toString(), this.resources))
			.withMessage("yozmnews.credential.kek-file(환경 변수 YOZMNEWS_CREDENTIAL_KEK_FILE) 가 가리키는 KEK 파일(" + file
					+ ")의 형식이 틀렸다\n  2번째 줄: 버전 1 이 겹친다\n  3번째 줄: 값은 32바이트여야 한다 (지금 3바이트)")
			.satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(ONES).doesNotContain(TWOS).doesNotContain("AQEB"));
	}

	@Test
	void doesNotStartWhenStoredKeysPointToAVersionMissingFromTheFile(@TempDir Path dir) throws IOException {
		KeyEncryptionKeys keys = KekConfiguration
			.load(Files.writeString(dir.resolve("kek"), "2:" + TWOS + "\n").toString(), this.resources);

		assertThatIllegalStateException()
			.isThrownBy(() -> KekConfiguration.requireVersionsInUse(keys,
					List.of(new KekVersionUse(1, 3), new KekVersionUse(2, 5), new KekVersionUse(4, 1))))
			.withMessage("저장된 LLM 키가 가리키는 KEK 버전이 yozmnews.credential.kek-file(환경 변수 YOZMNEWS_CREDENTIAL_KEK_FILE)"
					+ " 의 파일에 없다 — 버전 1 (키 3개), 버전 4 (키 1개)");
	}

	@Test
	void startsWhenEveryVersionInUseIsInTheFile(@TempDir Path dir) throws IOException {
		KeyEncryptionKeys keys = KekConfiguration
			.load(Files.writeString(dir.resolve("kek"), "1:" + ONES + "\n2:" + TWOS + "\n").toString(), this.resources);

		assertThatCode(() -> KekConfiguration.requireVersionsInUse(keys, List.of(new KekVersionUse(1, 3))))
			.doesNotThrowAnyException();
		assertThatCode(() -> KekConfiguration.requireVersionsInUse(keys, List.of())).doesNotThrowAnyException();
	}

	@Test
	void theContextStartsWithTheCipherOfTheFile(@TempDir Path dir) throws IOException {
		Path file = Files.writeString(dir.resolve("kek"), "1:" + ONES + "\n2:" + TWOS + "\n");

		this.runner.withPropertyValues("yozmnews.credential.kek-file=" + file)
			.withBean(LlmKeyRepository.class, () -> keysInUse(new KekVersionUse(1, 3)))
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(EnvelopeCipher.class).activeVersion()).isEqualTo(2);
			});
	}

	@Test
	void theContextDoesNotStartWithoutTheSetting() {
		this.runner.withBean(LlmKeyRepository.class, () -> keysInUse()).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.hasMessage("yozmnews.credential.kek-file(환경 변수 YOZMNEWS_CREDENTIAL_KEK_FILE) 가 없다"
						+ " — LLM 키 암호화 키(KEK) 파일의 경로를 적는다");
		});
	}

	@Test
	void theContextDoesNotStartWhenStoredKeysPointToAVersionMissingFromTheFile(@TempDir Path dir) throws IOException {
		Path file = Files.writeString(dir.resolve("kek"), "2:" + TWOS + "\n");

		this.runner.withPropertyValues("yozmnews.credential.kek-file=" + file)
			.withBean(LlmKeyRepository.class, () -> keysInUse(new KekVersionUse(1, 3), new KekVersionUse(2, 5)))
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).rootCause()
					.hasMessage("저장된 LLM 키가 가리키는 KEK 버전이 yozmnews.credential.kek-file(환경 변수"
							+ " YOZMNEWS_CREDENTIAL_KEK_FILE) 의 파일에 없다 — 버전 1 (키 3개)");
			});
	}

	/** 버전마다의 키 수만 답하는 저장소 대역 — 기동 검사가 읽는 것은 그것뿐이다 */
	private static LlmKeyRepository keysInUse(KekVersionUse... inUse) {
		return (LlmKeyRepository) Proxy.newProxyInstance(LlmKeyRepository.class.getClassLoader(),
				new Class<?>[] { LlmKeyRepository.class }, (proxy, method, args) -> switch (method.getName()) {
					case "versionsInUse" -> List.of(inUse);
					case "toString" -> "keysInUse";
					case "hashCode" -> System.identityHashCode(proxy);
					case "equals" -> proxy == args[0];
					default -> throw new UnsupportedOperationException(method.getName());
				});
	}

}
