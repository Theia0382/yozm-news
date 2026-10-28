package net.yozmnews.source.adapter.out.rss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * 본문을 상한까지만 읽는다 — 같으면 통과, 넘으면 실패.
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
