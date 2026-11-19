package net.yozmnews.digest.domain.output;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.digest.domain.item.ItemRules;
import tools.jackson.databind.json.JsonMapper;

/**
 * 출력 구조와 상한 — 정본 · docs/specs/digest.md §5 · digest-R4 · digest-R5
 * <p>
 * 상한은 측정 전 목표다. {@link #json()} 은 {@link #ROOT} 를 그린 것이고 따로 적지 않는다.
 */
public final class DigestOutputSchema {

	/** {@code overview} 의 최대 코드 포인트 */
	public static final int MAX_OVERVIEW = 1_000;

	/** 주요 뉴스 · 헤드라인 {@code title} 의 최대 코드 포인트 */
	public static final int MAX_TITLE = 200;

	/** 주요 뉴스 {@code summary} 의 최대 코드 포인트 */
	public static final int MAX_SUMMARY = 2_000;

	/** 헤드라인 {@code note} 의 최대 코드 포인트 */
	public static final int MAX_NOTE = 300;

	/**
	 * 주요 뉴스의 최대 개수 — 넘친 원소를 읽기가 버리는 방어 상한. 주요 뉴스 항목은 큰 슬롯 항목(사용자의 큰 슬롯 수 이하 — {@code user} 의 큰
	 * 슬롯 수 상한 {@code DigestVolume.MAX_BIG_SLOTS} 10) 더하기 {@link ItemRules#TOP_STORIES} 까지라 이 값이 그 합 이상이어야 한다 —
	 * 어느 쪽을 올리면 이 값을 함께 본다
	 */
	public static final int MAX_TOP_STORIES = 20;

	/**
	 * 헤드라인의 최대 개수 — 넘친 원소를 읽기가 버리는 방어 상한. 헤드라인 항목 수는 최대 후보 수(사건 수)를 넘지 않는다 — 이 값은 {@code user}
	 * 의 직접 입력한 최대 후보 수 상한({@code DigestVolume.MAX_COUNT} 150)과 같아야 한다. {@code digest} 는 그 상수를 읽지 못해 따로 적는다
	 * — 그 상한을 올리면 이 값을 함께 올린다
	 */
	public static final int MAX_HEADLINES = 150;

	/** 별칭 하나의 최대 코드 포인트 — 넘거나 빈 별칭은 항목과 맞지 않는다 */
	public static final int MAX_ALIAS = 16;

	/** 오늘의 흐름 */
	public static final String OVERVIEW = "overview";

	/** 주요 뉴스 목록 — 사용자 구획에서 주요 뉴스 항목의 칸 이름이기도 하다 */
	public static final String TOP_STORIES = "top_stories";

	/** 헤드라인 목록 — 사용자 구획에서 헤드라인 항목의 칸 이름이기도 하다 */
	public static final String HEADLINES = "headlines";

	/** 주요 뉴스 · 헤드라인이 가리키는 항목 별칭 */
	public static final String REF = "ref";

	/** 주요 뉴스 · 헤드라인의 제목 */
	public static final String TITLE = "title";

	/** 주요 뉴스의 요약 */
	public static final String SUMMARY = "summary";

	/** 헤드라인의 짧은 코멘트 */
	public static final String NOTE = "note";

	private static final OutputShape.Str ALIAS = new OutputShape.Str(MAX_ALIAS, OnBlank.KEEP, OverLimit.DROP_ITEM);

	private static final OutputShape.Str ITEM_TITLE = new OutputShape.Str(MAX_TITLE, OnBlank.UNREADABLE,
			OverLimit.DROP_ITEM);

	/** 출력 구조의 서술 */
	public static final OutputShape.Obj ROOT = new OutputShape.Obj(List.of(
			new OutputShape.Property(OVERVIEW, new OutputShape.Str(MAX_OVERVIEW, OnBlank.UNREADABLE, OverLimit.UNREADABLE), true,
					false),
			new OutputShape.Property(TOP_STORIES, new OutputShape.Arr(new OutputShape.Obj(List.of(
					new OutputShape.Property(REF, ALIAS, true, false),
					new OutputShape.Property(TITLE, ITEM_TITLE, true, false),
					new OutputShape.Property(SUMMARY, new OutputShape.Str(MAX_SUMMARY, OnBlank.UNREADABLE, OverLimit.DROP_ITEM),
							true, false))),
					MAX_TOP_STORIES), true, false),
			new OutputShape.Property(HEADLINES, new OutputShape.Arr(new OutputShape.Obj(List.of(
					new OutputShape.Property(REF, ALIAS, true, false),
					new OutputShape.Property(TITLE, ITEM_TITLE, true, false),
					new OutputShape.Property(NOTE, new OutputShape.Str(MAX_NOTE, OnBlank.ABSENT, OverLimit.DROP_VALUE), false,
							true))),
					MAX_HEADLINES), true, false)));

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
				boolean rejectsBlank = switch (str.onBlank()) {
					case UNREADABLE, KEEP -> true;
					case ABSENT -> false;
				};
				if (rejectsBlank) {
					node.put("minLength", 1);
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
