package net.yozmnews.source.domain.feed;

import java.util.Optional;

import net.yozmnews.source.FetchedArticle;
import org.jsoup.Jsoup;

/**
 * 피드 텍스트 정리 — docs/specs/source.md §4 · source-R40 · source-R45
 */
final class TextCleaner {

	private TextCleaner() {
	}

	/**
	 * 텍스트를 정리한다.
	 * @return 정리한 텍스트 — 남는 글자가 없으면 비어 있다
	 */
	static Optional<String> text(String raw) {
		String text = collapseWhitespace(Jsoup.parse(raw).text());
		return text.isEmpty() ? Optional.empty() : Optional.of(text);
	}

	/**
	 * 요약문을 정리한다.
	 * @return 정리한 뒤 상한에서 자른 요약문 — 남는 글자가 없으면 비어 있다
	 */
	static Optional<String> summary(String raw) {
		return text(raw).map(TextCleaner::truncate);
	}

	private static String truncate(String text) {
		if (text.codePointCount(0, text.length()) <= FetchedArticle.SUMMARY_MAX_CODE_POINTS) {
			return text;
		}
		return text.substring(0, text.offsetByCodePoints(0, FetchedArticle.SUMMARY_MAX_CODE_POINTS)).stripTrailing();
	}

	private static String collapseWhitespace(String text) {
		StringBuilder out = new StringBuilder(text.length());
		boolean pendingSpace = false;
		for (int index = 0; index < text.length();) {
			int codePoint = text.codePointAt(index);
			index += Character.charCount(codePoint);
			if (isWhitespace(codePoint)) {
				pendingSpace = !out.isEmpty();
				continue;
			}
			if (pendingSpace) {
				out.append(' ');
				pendingSpace = false;
			}
			out.appendCodePoint(codePoint);
		}
		return out.toString();
	}

	/** 공백류인지 본다 — Java 의 공백 문자에 줄바꿈 없는 공백(U+00A0 · U+2007 · U+202F)을 더한다 */
	private static boolean isWhitespace(int codePoint) {
		return Character.isWhitespace(codePoint) || codePoint == 0x00A0 || codePoint == 0x2007 || codePoint == 0x202F;
	}

}
