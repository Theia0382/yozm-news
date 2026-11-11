package net.yozmnews.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

/**
 * 임베더 정보 — 이름 · 모델 버전 · 차원의 값 검사
 */
class EmbedderInfoTests {

	@Test
	void refusesAMissingOrBlankName() {
		assertThatNullPointerException().isThrownBy(() -> new EmbedderInfo(null, "model-1", 3));
		assertThatIllegalArgumentException().isThrownBy(() -> new EmbedderInfo(" \t", "model-1", 3));
	}

	@Test
	void refusesAMissingOrBlankModelVersion() {
		assertThatNullPointerException().isThrownBy(() -> new EmbedderInfo("test", null, 3));
		assertThatIllegalArgumentException().isThrownBy(() -> new EmbedderInfo("test", "  ", 3));
	}

	@Test
	void refusesDimensionsBelowOne() {
		assertThatIllegalArgumentException().isThrownBy(() -> new EmbedderInfo("test", "model-1", 0));
	}

	@Test
	void acceptsUpTo4000DimensionsAndRefusesMore() {
		assertThat(new EmbedderInfo("test", "model-1", 4000).dimensions()).isEqualTo(4000);
		assertThatIllegalArgumentException().isThrownBy(() -> new EmbedderInfo("test", "model-1", 4001));
	}

	@Test
	void acceptsOneDimension() {
		EmbedderInfo info = new EmbedderInfo("test", "model-1", 1);

		assertThat(info.name()).isEqualTo("test");
		assertThat(info.modelVersion()).isEqualTo("model-1");
		assertThat(info.dimensions()).isEqualTo(1);
	}

}
