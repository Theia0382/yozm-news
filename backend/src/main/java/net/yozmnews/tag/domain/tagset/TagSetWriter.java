package net.yozmnews.tag.domain.tagset;

import java.math.BigDecimal;

import net.yozmnews.tag.TagSetThresholds;
import net.yozmnews.tag.TagThresholds;

/**
 * 태그 체계 원문 쓰기의 정본 — 버전의 내용과 그 버전의 지금 태그 기준 · 저장 기준으로 원문을 만든다 · docs/specs/tag.md
 * §4 · tag-R27.
 * <p>
 * 최상위 키 순서는 기본 체계 파일과 같고, 형식의 키 이름은 {@link TagSetBinder} 의 것이다. 계보 절 · 주석은 쓰지 않는다. 문자열은
 * 모두 큰따옴표로 쓴다 — 키 조각이 {@code null} · {@code true} · 수 꼴이어도 문자열로 읽힌다. 만든 원문을 {@link TagSetParser} 로
 * 다시 읽으면 내용과 처음 태그 기준 · 저장 기준이 같다.
 */
public final class TagSetWriter {

	private static final String ITEM = "- ";

	private TagSetWriter() {
	}

	/** 원문을 만든다 */
	public static String write(TagSetContent content, TagSetThresholds thresholds) {
		StringBuilder out = new StringBuilder();
		line(out, 0, TagSetBinder.QUESTIONS + ":");
		field(out, 1, TagSetBinder.TOPIC, quoted(content.topicTemplate().text()));
		field(out, 1, TagSetBinder.REGION, quoted(content.regionTemplate().text()));
		field(out, 1, TagSetBinder.FORMAT, quoted(content.formatQuestion()));
		field(out, 1, TagSetBinder.IMPORTANCE, quoted(content.importanceQuestion()));
		line(out, 0, TagSetBinder.THRESHOLDS + ":");
		threshold(out, TagSetBinder.TOPIC, thresholds.topic());
		threshold(out, TagSetBinder.REGION, thresholds.region());
		line(out, 0, TagSetBinder.TOPICS + ":");
		for (TopicCategoryDef topic : content.topics()) {
			item(out, 1, TagSetBinder.KEY, quoted(topic.key().value()));
			field(out, 2, TagSetBinder.NAME, quoted(topic.name()));
			field(out, 2, TagSetBinder.DESCRIPTION, quoted(topic.description()));
			line(out, 2, TagSetBinder.SUBTOPICS + ":");
			for (TagDef subtopic : topic.subtopics()) {
				item(out, 3, TagSetBinder.KEY, quoted(subtopic.key().leaf()));
				field(out, 4, TagSetBinder.NAME, quoted(subtopic.name()));
				field(out, 4, TagSetBinder.DESCRIPTION, quoted(subtopic.description()));
			}
		}
		line(out, 0, TagSetBinder.REGIONS + ":");
		for (TagDef region : content.regions()) {
			item(out, 1, TagSetBinder.KEY, quoted(region.key().value()));
			field(out, 2, TagSetBinder.NAME, quoted(region.name()));
			field(out, 2, TagSetBinder.DESCRIPTION, quoted(region.description()));
		}
		line(out, 0, TagSetBinder.FORMATS + ":");
		for (FormatDef format : content.formats()) {
			item(out, 1, TagSetBinder.KEY, quoted(format.key().value()));
			field(out, 2, TagSetBinder.NAME, quoted(format.name()));
			field(out, 2, TagSetBinder.DESCRIPTION, quoted(format.description()));
			if (format.excludedByDefault()) {
				field(out, 2, TagSetBinder.EXCLUDED_BY_DEFAULT, "true");
			}
		}
		line(out, 0, TagSetBinder.IMPORTANCE + ":");
		for (ImportanceLevelDef level : content.importance()) {
			item(out, 1, TagSetBinder.LEVEL, Integer.toString(level.level()));
			field(out, 2, TagSetBinder.DESCRIPTION, quoted(level.description()));
		}
		return out.toString();
	}

	private static void threshold(StringBuilder out, String kind, TagThresholds values) {
		line(out, 1, kind + ":");
		field(out, 2, TagSetBinder.TAG_THRESHOLD, number(values.tagThreshold()));
		field(out, 2, TagSetBinder.STORE_THRESHOLD, number(values.storeThreshold()));
	}

	/** 목록 항목의 첫 줄 — 들여쓰기 깊이는 항목 표지({@code - }) 앞까지, 뒤따르는 항목 줄은 한 단계 더 깊다 */
	private static void item(StringBuilder out, int depth, String key, String value) {
		line(out, depth, ITEM + key + ": " + value);
	}

	private static void field(StringBuilder out, int depth, String key, String value) {
		line(out, depth, key + ": " + value);
	}

	private static void line(StringBuilder out, int depth, String text) {
		out.append("  ".repeat(depth)).append(text).append('\n');
	}

	/** 끝의 0 을 지운 십진 표기 — 0 은 {@code 0} */
	static String number(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	/**
	 * 큰따옴표 문자열 — {@code \} · {@code "} 와, YAML 이 원문에 그대로 받지 않는 문자(C0 · C1 제어 문자 · DEL · BOM · U+FFFE ·
	 * U+FFFF · 짝 없는 서로게이트)는 {@code \}{@code uXXXX} 로 쓴다.
	 */
	static String quoted(String text) {
		StringBuilder out = new StringBuilder(text.length() + 2).append('"');
		text.codePoints().forEach(codePoint -> {
			if (codePoint == '"' || codePoint == '\\') {
				out.append('\\').appendCodePoint(codePoint);
			}
			else if (escaped(codePoint)) {
				out.append(String.format("\\u%04X", codePoint));
			}
			else {
				out.appendCodePoint(codePoint);
			}
		});
		return out.append('"').toString();
	}

	private static boolean escaped(int codePoint) {
		return codePoint < 0x20 || (codePoint >= 0x7F && codePoint <= 0x9F) || codePoint == 0xFEFF
				|| codePoint == 0xFFFE || codePoint == 0xFFFF
				|| (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE);
	}

}
