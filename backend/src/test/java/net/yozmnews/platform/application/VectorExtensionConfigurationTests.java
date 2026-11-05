package net.yozmnews.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import net.yozmnews.platform.domain.DatabaseExtensions;
import net.yozmnews.platform.domain.VectorExtension;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * pgvector 확장 요건 검사의 결선 — 설치 버전 읽기를 대체 입력으로 바꿔 컨텍스트를 띄워 본다. 대체 입력은 이름 {@code vector} 에만 값을 주고 그 밖의 이름은
 * 설치되지 않음으로 답한다.
 * <p>
 * 0.8 보다 낮은 갈래는 실제 DB 로 재현할 수 없어(pgvector 이미지에 그 버전의 설치 스크립트가 없다) 이 시험이 결선의 증거다.
 * <p>
 * 지키는 규칙: platform-R19
 */
class VectorExtensionConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(VectorExtensionConfiguration.class);

	@Test
	void startsWithTheInstalledVersion() {
		installed(Optional.of("0.8.7")).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(VectorExtension.class).version()).isEqualTo("0.8.7");
		});
	}

	@Test
	void failsWhenTheInstalledVersionIsLower() {
		installed(Optional.of("0.7.4")).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ALTER EXTENSION vector UPDATE");
		});
	}

	@Test
	void failsWhenTheExtensionIsMissing() {
		installed(Optional.empty()).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("CREATE EXTENSION vector");
		});
	}

	private ApplicationContextRunner installed(Optional<String> vectorVersion) {
		DatabaseExtensions extensions = name -> "vector".equals(name) ? vectorVersion : Optional.empty();
		return this.runner.withBean(DatabaseExtensions.class, () -> extensions);
	}

}
