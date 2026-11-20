package net.yozmnews.digest.domain.output;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.digest.domain.candidate.AliasedItem;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.item.ItemKind;
import tools.jackson.databind.json.JsonMapper;

/**
 * 출력 구조와 상한 — 정본 · docs/specs/digest.md §5 · digest-R4 · digest-R5
 * <p>
 * 출력 서술은 요청마다 그 항목 집합에서 만든다({@link #root}) — 주요 뉴스 · 헤드라인 칸은 항목 별칭을 키로 한 객체이고, JSON Schema 는 모든
 * 키를 필수로 그려 구조화 출력이 모든 항목을 쓰게 한다. 같은 항목 집합이면 같은 서술이다. 문자열 상한은 측정 전 목표다. {@link #json} 은 서술을
 * 그린 것이고 따로 적지 않는다.
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


	/** 오늘의 흐름 */
	public static final String OVERVIEW = "overview";

	/** 주요 뉴스 칸 — 항목 별칭을 키로 한 객체. 사용자 구획에서 주요 뉴스 항목의 칸 이름이기도 하다 */
	public static final String TOP_STORIES = "top_stories";

	/** 헤드라인 칸 — 항목 별칭을 키로 한 객체. 사용자 구획에서 헤드라인 항목의 칸 이름이기도 하다 */
	public static final String HEADLINES = "headlines";

	/** 주요 뉴스 · 헤드라인의 제목 */
	public static final String TITLE = "title";

	/** 주요 뉴스의 요약 */
	public static final String SUMMARY = "summary";

	/** 헤드라인의 짧은 코멘트 */
	public static final String NOTE = "note";

	private static final OutputShape.Str ITEM_TITLE = new OutputShape.Str(MAX_TITLE, OnBlank.UNREADABLE,
			OverLimit.DROP_ITEM);

	private static final OutputShape.Obj STORY = new OutputShape.Obj(List.of(
			new OutputShape.Property(TITLE, ITEM_TITLE, true, false),
			new OutputShape.Property(SUMMARY, new OutputShape.Str(MAX_SUMMARY, OnBlank.UNREADABLE, OverLimit.DROP_ITEM), true,
					false)));

	private static final OutputShape.Obj HEADLINE = new OutputShape.Obj(List.of(
			new OutputShape.Property(TITLE, ITEM_TITLE, true, false),
			new OutputShape.Property(NOTE, new OutputShape.Str(MAX_NOTE, OnBlank.ABSENT, OverLimit.DROP_VALUE), false, true)));

	/** 항목이 없는 칸의 서술 — 키 없는 빈 객체 */
	private static final OutputShape.Obj EMPTY = new OutputShape.Obj(List.of());

	private static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private DigestOutputSchema() {
	}

	/**
	 * 이번 요청의 출력 서술 — 개요와, 칸마다 그 칸의 항목 별칭(항목 순서)을 키로 한 객체. 칸에 항목이 없으면 그 칸은 빈 객체다
	 */
	public static OutputShape.Obj root(CandidateSet set) {
		Objects.requireNonNull(set, "set");
		return new OutputShape.Obj(List.of(
				new OutputShape.Property(OVERVIEW, new OutputShape.Str(MAX_OVERVIEW, OnBlank.UNREADABLE, OverLimit.UNREADABLE),
						true, false),
				new OutputShape.Property(TOP_STORIES, slot(set, ItemKind.TOP_STORY, STORY), true, false),
				new OutputShape.Property(HEADLINES, slot(set, ItemKind.HEADLINE, HEADLINE), true, false)));
	}

	private static OutputShape slot(CandidateSet set, ItemKind kind, OutputShape.Obj value) {
		List<String> aliases = set.items().stream().filter(item -> item.kind() == kind).map(AliasedItem::alias).toList();
		return aliases.isEmpty() ? EMPTY : new OutputShape.Keyed(aliases, value);
	}

	/**
	 * 서술을 JSON Schema(draft 2020-12) 문서로 그린다 — 객체는 모르는 속성을 허용하지 않고, 비었을 때 없음으로 받지 않는
	 * 문자열은 {@code minLength} 1, null 을 받는 속성은 타입에 {@code "null"} 을 더한다. 생성에 넘기는 문서라 읽기보다 엄격하다.
	 */
	public static String json(OutputShape.Obj root) {
		Objects.requireNonNull(root, "root");
		Map<String, Object> document = new LinkedHashMap<>();
		document.put("$schema", DIALECT);
		document.putAll(draw(root, false));
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
			case OutputShape.Keyed keyed -> {
				node.put("type", type("object", nullable));
				Map<String, Object> properties = new LinkedHashMap<>();
				for (String key : keyed.keys()) {
					properties.put(key, draw(keyed.value(), false));
				}
				node.put("properties", properties);
				node.put("required", keyed.keys());
				node.put("additionalProperties", false);
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
