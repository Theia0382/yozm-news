package net.yozmnews.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 이벤트를 읽을 수 있는지의 판정 — 값이면 참, 예외 · 클래스 연결 오류 · 값 없음이면 거짓.
 * <p>
 * 지키는 규칙: platform-R16
 */
class EventReadingTests {

	@Test
	void aValueIsReadable() {
		assertThat(EventReading.readable(() -> "event")).isTrue();
	}

	@Test
	void noValueIsNotReadable() {
		assertThat(EventReading.readable(() -> null)).isFalse();
	}

	@Test
	void aFailedReadIsNotReadable() {
		assertThat(EventReading.readable(() -> {
			throw new IllegalArgumentException("읽지 못했다");
		})).isFalse();
	}

	@Test
	void aMissingFieldTypeIsNotReadable() {
		assertThat(EventReading.readable(() -> {
			throw new NoClassDefFoundError("net/yozmnews/Gone");
		})).isFalse();
	}

}
