package net.yozmnews.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 설치된 pgvector 확장의 판정 — 스프링 없이 값을 직접 만든다. 기대값은 손으로 정한 버전 문자열이다.
 * <p>
 * {@code 0.10.0} 통과와 {@code 0.7.4} 거부가 함께 있어 문자열 비교로는 통과하지 못한다({@code "0.10.0"} 은 문자열로 {@code "0.8"} 보다 앞선다).
 * {@code 0} 거부는 없는 성분을 0 으로 채우지 않고 짧은 쪽 길이까지만 견주는 비교로는 통과하지 못한다.
 * <p>
 * 지키는 규칙: platform-R19
 */
class VectorExtensionTests {

	@ParameterizedTest
	@ValueSource(strings = { "0.8", "0.8.0", "0.8.7", "0.10.0", "1.0.0" })
	void acceptsTheMinimumVersionAndAbove(String version) {
		assertThat(new VectorExtension(version).version()).isEqualTo(version);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0.7.4", "0.7", "0.0.9", "0" })
	void rejectsALowerVersionAndTellsHowToUpdate(String version) {
		assertThatIllegalStateException().isThrownBy(() -> new VectorExtension(version))
			.withMessageContaining("버전 " + version + " 은")
			.withMessageContaining("최소 버전 0.8")
			.withMessageContaining("ALTER EXTENSION vector UPDATE");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "abc", "0.8.x", "0..8", " 0.8.7", "0.8.7-dev" })
	void rejectsAVersionThatIsNotNumericComponents(String version) {
		assertThatIllegalStateException().isThrownBy(() -> new VectorExtension(version))
			.withMessageContaining("'" + version + "'")
			.withMessageContaining("숫자 성분으로 읽을 수 없다");
	}

	@Test
	void rejectsAMissingExtensionAndTellsHowToInstall() {
		assertThatIllegalStateException().isThrownBy(() -> VectorExtension.installed(Optional.empty()))
			.withMessageContaining("설치되어 있지 않다")
			.withMessageContaining("CREATE EXTENSION vector");
	}

	@Test
	void takesAnInstalledVersion() {
		assertThat(VectorExtension.installed(Optional.of("0.8.7")).version()).isEqualTo("0.8.7");
	}

}
