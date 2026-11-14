package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.function.Consumer;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/**
 * 비밀 값 평문의 래퍼 — 문자열 표현이 값을 숨기고, 직렬화하면(Jackson 2 · 3 · Java 직렬화) 값 없이 실패한다.
 * <p>
 * 지키는 규칙: platform-R22
 */
class SecretValueTests {

	private static final String PLAIN = "sk-plain-0123456789-do-not-leak";

	@Test
	void revealsThePlainValueAndItsLength() {
		SecretValue secret = SecretValue.of(PLAIN);

		assertThat(secret.reveal()).isEqualTo("sk-plain-0123456789-do-not-leak");
		assertThat(secret.length()).isEqualTo(31);
	}

	@Test
	void hidesTheValueInItsStringForm() {
		SecretValue secret = SecretValue.of(PLAIN);

		assertThat(secret).hasToString("SecretValue[숨김]");
		assertThat("key=" + secret).isEqualTo("key=SecretValue[숨김]");
		assertThat(String.format("%s", secret)).isEqualTo("SecretValue[숨김]");
		assertThat(new Holder("openai", secret)).hasToString("Holder[provider=openai, key=SecretValue[숨김]]");
	}

	@Test
	void isEqualOnlyToItself() {
		SecretValue secret = SecretValue.of(PLAIN);

		assertThat(secret).isEqualTo(secret).isNotEqualTo(SecretValue.of(PLAIN));
	}

	@Test
	void rejectsAnEmptyValue() {
		assertThatIllegalArgumentException().isThrownBy(() -> SecretValue.of("")).withMessage("비밀 값이 비었다");
		assertThatIllegalArgumentException().isThrownBy(() -> SecretValue.of(null)).withMessage("비밀 값이 비었다");
	}

	@Test
	void failsWhenJackson3WritesIt() {
		tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder().build();

		refusesToSerialize(() -> mapper.writeValueAsString(SecretValue.of(PLAIN)));
		refusesToSerialize(() -> mapper.writeValueAsString(new Holder("openai", SecretValue.of(PLAIN))));
	}

	@Test
	void failsWhenJackson2WritesIt() {
		com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

		refusesToSerialize(() -> mapper.writeValueAsString(SecretValue.of(PLAIN)));
		refusesToSerialize(() -> mapper.writeValueAsString(new Holder("openai", SecretValue.of(PLAIN))));
	}

	@Test
	void jacksonWritesTheOtherFieldsOfTheHolder() throws Exception {
		// 대조 — 같은 매퍼가 비밀 값이 없는 레코드는 쓴다. 위의 실패가 매퍼 탓이 아니다
		assertThat(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(new Plain("openai")))
			.isEqualTo("{\"provider\":\"openai\"}");
		assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new Plain("openai")))
			.isEqualTo("{\"provider\":\"openai\"}");
	}

	@Test
	void isNotJavaSerializable() throws Exception {
		assertThat(SecretValue.of(PLAIN)).isNotInstanceOf(Serializable.class);
		try (ObjectOutputStream out = new ObjectOutputStream(new ByteArrayOutputStream())) {
			assertThatThrownBy(() -> out.writeObject(new SerializableHolder(SecretValue.of(PLAIN))))
				.isInstanceOf(NotSerializableException.class)
				.satisfies(withoutThePlainValue());
		}
	}

	private static void refusesToSerialize(ThrowingCallable write) {
		assertThatThrownBy(write).rootCause()
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SecretValue 는 직렬화하지 않는다");
		assertThatThrownBy(write).satisfies(withoutThePlainValue());
	}

	/** 예외와 그 원인 사슬의 문구 어디에도 평문이 없다 */
	private static Consumer<Throwable> withoutThePlainValue() {
		return thrown -> {
			for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
				assertThat(String.valueOf(cause.getMessage())).doesNotContain(PLAIN);
			}
		};
	}

	record Holder(String provider, SecretValue key) {

	}

	record Plain(String provider) {

	}

	record SerializableHolder(SecretValue key) implements Serializable {

	}

}
