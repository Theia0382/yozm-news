package net.yozmnews.event.domain.feature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.ko.POS;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * 핵심 말 추출의 정본 — 표현 텍스트를 Lucene Nori 로 나눠 고유명사 · 일반명사 · 외국어를 고른다 · docs/specs/event.md §4 · event-R2.
 * <p>
 * 분석기는 사용자 사전 없이, 복합어를 나누지 않고({@link KoreanTokenizer.DecompoundMode#NONE}), 미등록어를 한 글자씩 나누지 않고, 구두점을
 * 버린다. 분석기 하나를 여러 스레드에서 쓴다 — 토큰 흐름은 스레드마다 따로 만들어진다. Nori 사전은 첫 분석 때 읽힌다.
 */
public final class KeyTermExtractor implements AutoCloseable {

	/** 고르는 품사 — 고유명사 · 일반명사 · 외국어 */
	public static final Set<POS.Tag> KEPT_TAGS = Collections
		.unmodifiableSet(EnumSet.of(POS.Tag.NNP, POS.Tag.NNG, POS.Tag.SL));

	private static final String FIELD = "text";

	private final Analyzer analyzer = new Analyzer() {

		@Override
		protected TokenStreamComponents createComponents(String fieldName) {
			return new TokenStreamComponents(new KoreanTokenizer(KoreanTokenizer.DEFAULT_TOKEN_ATTRIBUTE_FACTORY, null,
					KoreanTokenizer.DecompoundMode.NONE, false, true));
		}

	};

	/**
	 * 처음 나온 순서, 같은 말은 한 번. 품사는 토큰의 왼쪽 품사다. 한 코드 포인트짜리 한글({@link Character.UnicodeScript#HANGUL}) 일반명사는
	 * 버린다 — 고유명사 · 한자 · 영문은 한 코드 포인트라도 남긴다. 외국어는 {@link Locale#ROOT} 소문자로 바꾼다.
	 * @return 핵심 말 — 뽑을 말이 없으면 빈 목록
	 */
	public List<String> extract(String text) {
		Objects.requireNonNull(text, "text");
		Set<String> terms = new LinkedHashSet<>();
		try (TokenStream stream = this.analyzer.tokenStream(FIELD, text)) {
			CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
			PartOfSpeechAttribute pos = stream.addAttribute(PartOfSpeechAttribute.class);
			stream.reset();
			while (stream.incrementToken()) {
				POS.Tag tag = pos.getLeftPOS();
				if (tag == null || !KEPT_TAGS.contains(tag)) {
					continue;
				}
				String value = term.toString();
				if (tag == POS.Tag.NNG && isSingleHangul(value)) {
					continue;
				}
				terms.add((tag == POS.Tag.SL) ? value.toLowerCase(Locale.ROOT) : value);
			}
			stream.end();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return List.copyOf(terms);
	}

	private static boolean isSingleHangul(String value) {
		return value.codePointCount(0, value.length()) == 1
				&& Character.UnicodeScript.of(value.codePointAt(0)) == Character.UnicodeScript.HANGUL;
	}

	@Override
	public void close() {
		this.analyzer.close();
	}

}
