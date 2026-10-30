package net.yozmnews.source.domain.feed;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 사전 보정 {@code strip-invalid-control-chars} — docs/specs/source.md §4 · source-R38
 */
final class XmlCharacters {

	private static final Pattern NUMERIC_REFERENCE = Pattern.compile("&#(?:([0-9]+)|[xX]([0-9a-fA-F]+));");

	/** 앞의 0 을 뺀 자릿수가 이보다 길면 U+10FFFF 를 넘으므로 값을 읽지 않는다(10진 1114111 이 7자리) */
	private static final int MAX_DIGITS = 7;

	private XmlCharacters() {
	}

	/** XML 1.0 의 {@code Char} 인지 본다 */
	private static boolean isXmlChar(int codePoint) {
		return codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD || (codePoint >= 0x20 && codePoint <= 0xD7FF)
				|| (codePoint >= 0xE000 && codePoint <= 0xFFFD) || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
	}

	static String strip(String text) {
		StringBuilder out = new StringBuilder(text.length());
		Matcher matcher = NUMERIC_REFERENCE.matcher(text);
		int index = 0;
		while (index < text.length()) {
			int verbatimEnd = MarkupScanner.verbatimEnd(text, index);
			if (verbatimEnd >= 0) {
				// 구간 안의 참조처럼 보이는 글자는 참조가 아니다 — 쓸 수 없는 문자만 지운다
				appendXmlChars(text, index, verbatimEnd, out);
				index = verbatimEnd;
				continue;
			}
			if (text.charAt(index) == '&') {
				matcher.region(index, text.length());
				if (matcher.lookingAt()) {
					if (refersToXmlChar(matcher)) {
						out.append(matcher.group());
					}
					index = matcher.end();
					continue;
				}
			}
			int codePoint = text.codePointAt(index);
			if (isXmlChar(codePoint)) {
				out.appendCodePoint(codePoint);
			}
			index += Character.charCount(codePoint);
		}
		return out.toString();
	}

	private static void appendXmlChars(String text, int from, int to, StringBuilder out) {
		int index = from;
		while (index < to) {
			int codePoint = text.codePointAt(index);
			if (isXmlChar(codePoint)) {
				out.appendCodePoint(codePoint);
			}
			index += Character.charCount(codePoint);
		}
	}

	private static boolean refersToXmlChar(Matcher matcher) {
		String decimal = matcher.group(1);
		String digits = ((decimal != null) ? decimal : matcher.group(2)).replaceFirst("^0+(?=.)", "");
		if (digits.length() > MAX_DIGITS) {
			return false;
		}
		long value = Long.parseLong(digits, (decimal != null) ? 10 : 16);
		return value <= Character.MAX_CODE_POINT && isXmlChar((int) value);
	}

}
