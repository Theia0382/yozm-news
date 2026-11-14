package net.yozmnews.credential.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import net.yozmnews.credential.domain.crypto.EnvelopeCipher;
import net.yozmnews.credential.domain.crypto.KekFile;
import net.yozmnews.credential.domain.crypto.KekFileRead;
import net.yozmnews.credential.domain.crypto.KeyEncryptionKeys;
import net.yozmnews.credential.domain.key.KekVersionUse;
import net.yozmnews.credential.domain.key.LlmKeyRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * KEK 의 기동 적재와 검사 — docs/specs/credential.md §4 · credential-R5 · credential-R6
 * <p>
 * 설정이 가리키는 파일을 읽어 형식을 판정하고, 저장된 키가 가리키는 버전이 모두 설정에 있는지 본다. 어기면 기동하지 않는다 — 문구에 값을 담지
 * 않는다. 역할 애너테이션이 없다(두 역할 공통).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CredentialProperties.class)
class KekConfiguration {

	private static final String PROPERTY = CredentialProperties.PREFIX + ".kek-file";

	private static final String VARIABLE = "YOZMNEWS_CREDENTIAL_KEK_FILE";

	/** 설정 값이 클래스패스 위치임을 알리는 접두 — 시험이 쓴다 */
	private static final String CLASSPATH = "classpath:";

	private static final Logger logger = LoggerFactory.getLogger(KekConfiguration.class);

	@Bean
	KeyEncryptionKeys keyEncryptionKeys(CredentialProperties properties, ResourceLoader resourceLoader) {
		KeyEncryptionKeys keys = load(properties.kekFile(), resourceLoader);
		logger.info("LLM 키 암호화 KEK 버전 {} — 새 키는 버전 {} 으로 감싼다", keys.versions(), keys.activeVersion());
		return keys;
	}

	/** 저장소는 DB 초기화(Flyway) 뒤에 만들어지므로 첫 기동에서도 키 테이블을 본다 */
	@Bean
	EnvelopeCipher envelopeCipher(KeyEncryptionKeys keys, LlmKeyRepository repository) {
		requireVersionsInUse(keys, repository.versionsInUse());
		return new EnvelopeCipher(keys);
	}

	/**
	 * KEK 파일을 읽어 검증한다.
	 * @param kekFile 파일 경로 — {@code classpath:} 로 시작하면 클래스패스 위치
	 * @throws IllegalStateException 설정이 없거나 비었거나, 파일이 없거나 읽지 못했거나, 형식이 틀렸을 때 — 문구에 설정 이름 · 경로 · 문제의
	 * 줄 번호와 까닭을 담고 값은 담지 않는다
	 */
	static KeyEncryptionKeys load(@Nullable String kekFile, ResourceLoader resourceLoader) {
		String source = PROPERTY + "(환경 변수 " + VARIABLE + ")";
		if (kekFile == null || kekFile.isBlank()) {
			throw new IllegalStateException(source + " 가 없다 — LLM 키 암호화 키(KEK) 파일의 경로를 적는다");
		}
		Resource resource = kekFile.startsWith(CLASSPATH) ? resourceLoader.getResource(kekFile)
				: new FileSystemResource(kekFile);
		String what = source + " 가 가리키는 KEK 파일(" + kekFile + ")";
		if (!resource.isReadable()) {
			throw new IllegalStateException(what + "이 없거나 읽을 수 없다");
		}
		String text;
		try {
			text = resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException(what + "을 읽지 못했다 — " + ex.getClass().getSimpleName());
		}
		return switch (KekFile.parse(text)) {
			case KekFileRead.Parsed parsed -> parsed.keys();
			case KekFileRead.Invalid invalid -> throw new IllegalStateException(what + "의 형식이 틀렸다\n"
					+ invalid.problems().stream().map(problem -> "  " + problem).collect(Collectors.joining("\n")));
		};
	}

	/**
	 * 저장된 키가 가리키는 KEK 버전이 모두 설정에 있는지 본다.
	 * @throws IllegalStateException 설정에 없는 버전을 가리키는 키가 있을 때 — 문구에 그 버전과 키의 수를 담는다
	 */
	static void requireVersionsInUse(KeyEncryptionKeys keys, List<KekVersionUse> inUse) {
		List<KekVersionUse> missing = inUse.stream().filter(use -> !keys.has(use.kekVersion())).toList();
		if (!missing.isEmpty()) {
			throw new IllegalStateException("저장된 LLM 키가 가리키는 KEK 버전이 " + PROPERTY + "(환경 변수 " + VARIABLE
					+ ") 의 파일에 없다 — "
					+ missing.stream()
						.map(use -> "버전 " + use.kekVersion() + " (키 " + use.keys() + "개)")
						.collect(Collectors.joining(", ")));
		}
	}

}
