package net.yozmnews.source.domain.feed;

import java.util.Optional;

import net.yozmnews.source.FetchedArticle;
import org.jsoup.Jsoup;

/**
 * 피드의 제목 · 요약문 · 메타데이터 텍스트 정리 — HTML 로 보고 텍스트만 얻은 뒤(태그 제거 · 엔티티 해제), 공백류의 연속을 공백 하나로
 * 바꾸고 앞뒤를 지운다. 요약문은 {@link FetchedArticle#SUMMARY_MAX_CODE_POINTS} 에서 자른다.
 */
final class TextCleaner {

	private TextCleaner() {
	}

	/** @return 정리한 텍스트 — 남는 글자가 없으면 빈 값 */
	static Optional<String> text(String raw) {
		String text = collapseWhitespace(Jsoup.parse(raw).text());
		return text.isEmpty() ? Optional.empty() : Optional.of(text);
	}

	/** @return 정리한 뒤 상한에서 자른 요약문 — 남는 글자가 없으면 빈 값 */
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

	/** Java 의 공백 문자에 줄바꿈 없는 공백(U+00A0 · U+2007 · U+202F)을 더한다 */
	private static boolean isWhitespace(int codePoint) {
		return Character.isWhitespace(codePoint) || codePoint == 0x00A0 || codePoint == 0x2007 || codePoint == 0x202F;
	}

}
