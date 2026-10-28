package net.yozmnews.source.domain.feed;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Entities;

/**
 * 사전 보정 {@code replace-undefined-entities} — XML 이 모르는 엔티티 참조를 고친다. 숫자 참조와 XML 기본 엔티티는 그대로 두고,
 * HTML 이름 엔티티는 같은 문자의 숫자 참조로, 그 밖의 {@code &} 는 {@code &amp;} 로 바꿔 글자 그대로 남긴다.
 */
final class EntityReferences {

	private static final Set<String> XML_PREDEFINED = Set.of("amp", "lt", "gt", "quot", "apos");

	private static final Pattern REFERENCE = Pattern.compile("&(?:#(?:[0-9]+|[xX][0-9a-fA-F]+)|([A-Za-z][A-Za-z0-9]*));");

	private EntityReferences() {
	}

	static String repair(String text) {
		StringBuilder out = new StringBuilder(text.length());
		Matcher matcher = REFERENCE.matcher(text);
		int index = 0;
		while (index < text.length()) {
			int verbatimEnd = MarkupScanner.verbatimEnd(text, index);
			if (verbatimEnd >= 0) {
				out.append(text, index, verbatimEnd);
				index = verbatimEnd;
				continue;
			}
			char c = text.charAt(index);
			if (c != '&') {
				out.append(c);
				index++;
				continue;
			}
			matcher.region(index, text.length());
			if (!matcher.lookingAt()) {
				out.append("&amp;");
				index++;
				continue;
			}
			String name = matcher.group(1);
			if (name == null || XML_PREDEFINED.contains(name)) {
				out.append(matcher.group());
			}
			else if (Entities.isNamedEntity(name)) {
				Entities.getByName(name)
					.codePoints()
					.forEach(cp -> out.append("&#x").append(Integer.toHexString(cp).toUpperCase(Locale.ROOT)).append(';'));
			}
			else {
				out.append("&amp;").append(name).append(';');
			}
			index = matcher.end();
		}
		return out.toString();
	}

}
