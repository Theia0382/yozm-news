package net.yozmnews.source.adapter.out.rss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * 응답 본문의 상한 읽기 — 메모리 스트림으로 본다.
 * <p>
 * 지키는 규칙: source-R28
 */
class BoundedBodyTests {

	@Test
	void readsABodyOfExactlyTheLimit() throws IOException {
		assertThat(BoundedBody.read(new ByteArrayInputStream(new byte[20_000]), 20_000)).hasSize(20_000);
	}

	@Test
	void failsOneByteOverTheLimit() {
		assertThatExceptionOfType(BodyTooLargeException.class)
			.isThrownBy(() -> BoundedBody.read(new ByteArrayInputStream(new byte[20_001]), 20_000));
	}

	@Test
	void readsAnEmptyBody() throws IOException {
		assertThat(BoundedBody.read(new ByteArrayInputStream(new byte[0]), 1)).isEmpty();
	}

}
