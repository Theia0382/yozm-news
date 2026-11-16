package net.yozmnews.digest.domain.output;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

/**
 * 출력 구조와 상한 — 정본 · docs/specs/digest.md §5 · digest-R4 · digest-R5
 * <p>
 * 상한은 측정 전 목표다. {@link #json()} 은 {@link #ROOT} 를 그린 것이고 따로 적지 않는다.
 */
public final class DigestOutputSchema {

	/** {@code overview} 의 최대 코드 포인트 */
	public static final int MAX_OVERVIEW = 1_000;

	/** 주요 뉴스 {@code title} 의 최대 코드 포인트 */
	public static final int MAX_TITLE = 200;

	/** 주요 뉴스 {@code summary} 의 최대 코드 포인트 */
	public static final int MAX_SUMMARY = 2_000;

	/** 추가 헤드라인 {@code note} 의 최대 코드 포인트 */
	public static final int MAX_NOTE = 300;

	/** 주요 뉴스의 최대 개수 */
	public static final int MAX_TOP_STORIES = 20;

	/** 주요 뉴스 하나의 최대 인용 수 */
	public static final int MAX_REFS_PER_STORY = 20;

	/** 추가 헤드라인의 최대 개수 */
	public static final int MAX_MORE_HEADLINES = 60;

	/** 별칭 하나의 최대 코드 포인트 — 넘거나 빈 별칭은 후보와 맞지 않는다 */
	public static final int MAX_ALIAS = 16;

	/** 오늘의 흐름 */
	public static final String OVERVIEW = "overview";

	/** 주요 뉴스 목록 */
	public static final String TOP_STORIES = "top_stories";

	/** 추가 헤드라인 목록 */
	public static final String MORE_HEADLINES = "more_headlines";

	/** 주요 뉴스의 제목 */
	public static final String TITLE = "title";

	/** 주요 뉴스의 요약 */
	public static final String SUMMARY = "summary";

	/** 주요 뉴스가 인용한 별칭 목록 */
	public static final String REFS = "refs";

	/** 추가 헤드라인이 인용한 별칭 */
	public static final String REF = "ref";

	/** 추가 헤드라인의 한 줄 코멘트 */
	public static final String NOTE = "note";

	private static final OutputShape.Str ALIAS = new OutputShape.Str(MAX_ALIAS, OnBlank.KEEP, OverLimit.DROP_ITEM);

	/** 출력 구조의 서술 */
	public static final OutputShape.Obj ROOT = new OutputShape.Obj(List.of(
			new OutputShape.Property(OVERVIEW, new OutputShape.Str(MAX_OVERVIEW, OnBlank.UNREADABLE, OverLimit.UNREADABLE), true,
					false),
			new OutputShape.Property(TOP_STORIES, new OutputShape.Arr(new OutputShape.Obj(List.of(
					new OutputShape.Property(TITLE, new OutputShape.Str(MAX_TITLE, OnBlank.UNREADABLE, OverLimit.DROP_ITEM), true,
							false),
					new OutputShape.Property(SUMMARY, new OutputShape.Str(MAX_SUMMARY, OnBlank.UNREADABLE, OverLimit.DROP_ITEM),
							true, false),
					new OutputShape.Property(REFS, new OutputShape.Arr(ALIAS, MAX_REFS_PER_STORY), true, false))),
					MAX_TOP_STORIES), true, false),
			new OutputShape.Property(MORE_HEADLINES, new OutputShape.Arr(new OutputShape.Obj(List.of(
					new OutputShape.Property(REF, ALIAS, true, false),
					new OutputShape.Property(NOTE, new OutputShape.Str(MAX_NOTE, OnBlank.ABSENT, OverLimit.DROP_VALUE), false,
							true))),
					MAX_MORE_HEADLINES), true, false)));

	private static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private DigestOutputSchema() {
	}

	/**
	 * {@link #ROOT} 를 JSON Schema(draft 2020-12) 문서로 그린다 — 객체는 모르는 속성을 허용하지 않고, 비었을 때 없음으로 받지 않는
	 * 문자열은 {@code minLength} 1, null 을 받는 속성은 타입에 {@code "null"} 을 더한다. 생성에 넘기는 문서라 읽기보다 엄격하다.
	 */
	public static String json() {
		Map<String, Object> document = new LinkedHashMap<>();
		document.put("$schema", DIALECT);
		document.putAll(draw(ROOT, false));
		return JSON.writeValueAsString(document);
	}

	private static Map<String, Object> draw(OutputShape shape, boolean nullable) {
		Map<String, Object> node = new LinkedHashMap<>();
		switch (shape) {
			case OutputShape.Obj obj -> {
				node.put("type", type("object", nullable));
				Map<String, Object> properties = new LinkedHashMap<>();
				List<String> required = new ArrayList<>();
				for (OutputShape.Property property : obj.properties()) {
					properties.put(property.name(), draw(property.shape(), property.nullable()));
					if (property.required()) {
						required.add(property.name());
					}
				}
				node.put("properties", properties);
				node.put("required", required);
				node.put("additionalProperties", false);
			}
			case OutputShape.Arr arr -> {
				node.put("type", type("array", nullable));
				node.put("items", draw(arr.item(), false));
				node.put("maxItems", arr.maxItems());
			}
			case OutputShape.Str str -> {
				node.put("type", type("string", nullable));
				switch (str.onBlank()) {
					case UNREADABLE, KEEP -> node.put("minLength", 1);
					case ABSENT -> {
					}
				}
				node.put("maxLength", str.maxCodePoints());
			}
		}
		return node;
	}

	private static Object type(String type, boolean nullable) {
		return nullable ? List.of(type, "null") : type;
	}

}
