package net.yozmnews.evaluation.adapter.out.fake;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import net.yozmnews.evaluation.EmbedderInfo;
import net.yozmnews.evaluation.Embedding;
import net.yozmnews.evaluation.Embeddings;
import net.yozmnews.evaluation.domain.embedder.ArticleEmbedder;

/**
 * 임베더의 로컬 대체물 — 문자 2-그램 특징 해싱 · docs/specs/evaluation.md §4 · evaluation-R47.
 * <p>
 * 벡터 규칙은 Spec 이 정본이다. 외부 자원이 없다.
 */
public final class FakeArticleEmbedder implements ArticleEmbedder {

	/** 임베더 이름 */
	public static final String NAME = "fake";

	/** 모델 버전 — 규칙을 바꾸면 올린다 */
	public static final String MODEL_VERSION = "fake-char-bigram-256-1";

	/** 벡터 차원 */
	public static final int DIMENSIONS = 256;

	private static final EmbedderInfo INFO = new EmbedderInfo(NAME, MODEL_VERSION, DIMENSIONS);

	@Override
	public EmbedderInfo info() {
		return INFO;
	}

	/**
	 * @throws IllegalArgumentException 2-그램이 하나도 없는(공백뿐인) 텍스트가 있을 때
	 */
	@Override
	public Embeddings embed(List<String> texts) {
		List<Embedding> embeddings = new ArrayList<>(texts.size());
		for (String text : texts) {
			embeddings.add(embed(text));
		}
		return new Embeddings(embeddings);
	}

	private static Embedding embed(String text) {
		Objects.requireNonNull(text, "text");
		double[] counts = new double[DIMENSIONS];
		int grams = 0;
		for (String word : words(text)) {
			int[] codePoints = word.codePoints().toArray();
			if (codePoints.length == 1) {
				add(counts, new String(codePoints, 0, 1));
				grams++;
				continue;
			}
			for (int i = 0; i + 1 < codePoints.length; i++) {
				add(counts, new String(codePoints, i, 2));
				grams++;
			}
		}
		if (grams == 0) {
			throw new IllegalArgumentException("2-그램이 없는 텍스트다");
		}
		float[] values = new float[DIMENSIONS];
		for (int i = 0; i < DIMENSIONS; i++) {
			values[i] = (float) counts[i];
		}
		return Embedding.normalized(values);
	}

	private static void add(double[] counts, String gram) {
		counts[Math.floorMod(gram.toLowerCase(Locale.ROOT).hashCode(), DIMENSIONS)]++;
	}

	private static List<String> words(String text) {
		List<String> words = new ArrayList<>();
		StringBuilder word = new StringBuilder();
		text.codePoints().forEach(codePoint -> {
			if (Character.isWhitespace(codePoint)) {
				if (!word.isEmpty()) {
					words.add(word.toString());
					word.setLength(0);
				}
			}
			else {
				word.appendCodePoint(codePoint);
			}
		});
		if (!word.isEmpty()) {
			words.add(word.toString());
		}
		return words;
	}

}
