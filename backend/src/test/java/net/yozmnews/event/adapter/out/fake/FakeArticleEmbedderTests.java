package net.yozmnews.event.adapter.out.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import net.yozmnews.event.domain.feature.EmbedderInfo;
import net.yozmnews.event.domain.feature.Embeddings;
import org.junit.jupiter.api.Test;

/**
 * 임베더 로컬 대체물 — 같은 텍스트면 같은 벡터, 2-그램이 겹칠수록 가깝고, 소문자로 같게 보고, 길이 1 이다.
 * <p>
 * 오라클은 손으로 셀 수 있는 입력(2-그램 하나 · 둘)의 칸 값과 텍스트 쌍의 대소다 — 칸 위치는 단언하지 않는다. 시험 문장은 지어낸 것이다.
 * <p>
 * 지키는 규칙: event-R9
 */
class FakeArticleEmbedderTests {

	private final FakeArticleEmbedder embedder = new FakeArticleEmbedder();

	@Test
	void theInfoNamesTheFakeAndItsDimensions() {
		assertThat(this.embedder.info()).isEqualTo(new EmbedderInfo("fake", "fake-char-bigram-256-1", 256));
	}

	@Test
	void theSameTextGetsTheSameVector() {
		assertThat(vector("기준금리 동결")).containsExactly(vector("기준금리 동결"));
	}

	@Test
	void aVectorHas256DimensionsAndLengthOne() {
		float[] values = vector("기준금리 동결");

		assertThat(values).hasSize(256);
		assertThat(length(values)).isCloseTo(1.0, within(1e-6));
	}

	@Test
	void textsSharingBigramsAreCloserThanTextsSharingNone() {
		float[] frozen = vector("기준금리 동결");

		assertThat(cosine(frozen, vector("기준금리 인하"))).isGreaterThan(cosine(frozen, vector("축구 결승")));
	}

	@Test
	void aWordOfOneCodePointStillGetsAVector() {
		assertThat(length(vector("美"))).isCloseTo(1.0, within(1e-6));
	}

	@Test
	void oneBigramFillsExactlyOneCell() {
		float[] values = vector("ab");

		assertThat(cells(values, 1.0f)).isEqualTo(1);
		assertThat(cells(values, 0.0f)).isEqualTo(255);
	}

	@Test
	void twoBigramsFillTwoCellsOfOneOverRootTwo() {
		float[] values = vector("abc");

		assertThat(cells(values, 0.70710677f)).isEqualTo(2);
		assertThat(cells(values, 0.0f)).isEqualTo(254);
	}

	@Test
	void bigramsAreLowercased() {
		// 두 글자가 모두 대문자인 ASCII 2-그램은 소문자 2-그램과 hashCode 가 1024 차이라 256 칸에서 같은 칸이다
		// — 대소문자가 섞인 낱말이 소문자화를 가른다
		assertThat(vector("Hbm 판매")).containsExactly(vector("hbm 판매"));
		assertThat(vector("HBM 판매")).containsExactly(vector("hbm 판매"));
		assertThat(vector("HBM 판매")).isNotEqualTo(vector("HBN 판매"));
	}

	@Test
	void aBlankTextIsRejected() {
		assertThatThrownBy(() -> this.embedder.embed(List.of("  "))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void textsGetVectorsInTheirOrder() {
		assertThat(this.embedder.embed(List.of("ab", "기준금리 동결")))
			.isEqualTo(new Embeddings(List.of(this.embedder.embed(List.of("ab")).vectors().get(0),
					this.embedder.embed(List.of("기준금리 동결")).vectors().get(0))));
	}

	private float[] vector(String text) {
		return this.embedder.embed(List.of(text)).vectors().get(0).values();
	}

	private static double length(float[] values) {
		double sum = 0;
		for (float value : values) {
			sum += (double) value * value;
		}
		return Math.sqrt(sum);
	}

	private static double cosine(float[] left, float[] right) {
		double dot = 0;
		for (int i = 0; i < left.length; i++) {
			dot += (double) left[i] * right[i];
		}
		return dot / (length(left) * length(right));
	}

	private static long cells(float[] values, float expected) {
		long count = 0;
		for (float value : values) {
			if (Math.abs(value - expected) <= 1e-6f) {
				count++;
			}
		}
		return count;
	}

}
