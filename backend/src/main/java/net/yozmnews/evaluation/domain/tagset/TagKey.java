package net.yozmnews.evaluation.domain.tagset;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 태그 키 — docs/specs/evaluation.md §2 · evaluation-R6.
 * <p>
 * 대분류 · 지역 · 형식은 조각 하나, 주제 중분류는 대분류 키를 점으로 붙인 전체 키({@code politics.executive})다.
 */
public record TagKey(String value) {

	/** 조각 하나의 길이 상한(문자 수) */
	public static final int MAX_SEGMENT_LENGTH = 40;

	private static final Pattern SEGMENT = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

	/**
	 * @throws IllegalArgumentException 조각 하나이거나 조각 둘을 점 하나로 이은 꼴이 아닐 때
	 */
	public TagKey {
		Objects.requireNonNull(value, "value");
		String[] segments = value.split("\\.", -1);
		if (segments.length > 2 || !isSegment(segments[0]) || (segments.length == 2 && !isSegment(segments[1]))) {
			throw new IllegalArgumentException("태그 키 형식이 아니다: " + value);
		}
	}

	/** 조각 하나로 된 키를 만든다 */
	public static TagKey of(String segment) {
		if (!isSegment(segment)) {
			throw new IllegalArgumentException("태그 키 조각 형식이 아니다: " + segment);
		}
		return new TagKey(segment);
	}

	/** 대분류 키에 중분류 조각을 붙인 전체 키를 만든다 */
	public static TagKey child(TagKey parent, String segment) {
		if (parent.value.contains(".") || !isSegment(segment)) {
			throw new IllegalArgumentException("중분류 키를 만들 수 없다: " + parent.value + " · " + segment);
		}
		return new TagKey(parent.value + "." + segment);
	}

	/** 태그 키인가 — 조각 하나이거나 조각 둘을 점 하나로 이은 꼴(evaluation-R6) */
	public static boolean isKey(String text) {
		String[] segments = text.split("\\.", -1);
		return segments.length <= 2 && isSegment(segments[0]) && (segments.length == 1 || isSegment(segments[1]));
	}

	/** 중분류 전체 키인가 — 조각 둘 */
	public boolean isChild() {
		return this.value.indexOf('.') >= 0;
	}

	/** 마지막 조각 — 중분류 전체 키면 점 뒤 조각, 조각 하나면 그 조각 */
	public String leaf() {
		return this.value.substring(this.value.indexOf('.') + 1);
	}

	/** 키 조각인가 — 영문 소문자 · 숫자를 하이픈 하나로 이은 40자 이하 */
	public static boolean isSegment(String text) {
		return text.length() <= MAX_SEGMENT_LENGTH && SEGMENT.matcher(text).matches();
	}

	@Override
	public String toString() {
		return this.value;
	}

}
