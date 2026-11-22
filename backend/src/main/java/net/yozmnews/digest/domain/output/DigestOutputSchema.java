package net.yozmnews.digest.domain.output;

import java.util.List;
import java.util.Objects;

import net.yozmnews.digest.domain.candidate.AliasedItem;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.item.ItemKind;
import net.yozmnews.platform.OutputShape;
import net.yozmnews.platform.OutputShape.OnBlank;
import net.yozmnews.platform.OutputShape.OverLimit;

/**
 * 출력 구조와 상한 — 정본 · docs/specs/digest.md §5 · digest-R4 · digest-R5
 * <p>
 * 출력 서술은 요청마다 그 항목 집합에서 만든다({@link #root}) — 주요 뉴스 칸은 항목 별칭을 키로 한 객체이고, JSON Schema 는 모든 키를
 * 필수로 그려 구조화 출력이 모든 주요 뉴스 항목을 쓰게 한다. 헤드라인 칸은 원소마다 항목 별칭({@link #REF})을 단 배열이다 — 키 꼴은 항목
 * 수만큼 문법이 커져 공급자가 거부하므로 수에 한도가 없는 헤드라인은 배열로 두고, 출력에 빠진 헤드라인 항목은 부르는 쪽이 처분한다. 같은
 * 항목 집합이면 같은 서술이다. 문자열 상한은 측정 전 목표다. 생성에 넘기는 JSON Schema 문서는 서술을 그린 것({@code StructuredOutputSchema.json})이고 따로 적지 않는다.
 */
public final class DigestOutputSchema {

	/** {@code overview} 의 최대 코드 포인트 */
	public static final int MAX_OVERVIEW = 1_500;

	/** 주요 뉴스 · 헤드라인 {@code title} 의 최대 코드 포인트 */
	public static final int MAX_TITLE = 200;

	/** 주요 뉴스 {@code summary} 의 최대 코드 포인트 */
	public static final int MAX_SUMMARY = 2_000;

	/** 헤드라인 {@code note} 의 최대 코드 포인트 */
	public static final int MAX_NOTE = 300;

	/** 헤드라인 원소 별칭의 최대 코드 포인트 — 넘으면 그 원소를 버린다 */
	public static final int MAX_ALIAS = 16;

	/** 오늘의 흐름 */
	public static final String OVERVIEW = "overview";

	/** 주요 뉴스 칸 — 항목 별칭을 키로 한 객체. 사용자 구획에서 주요 뉴스 항목의 칸 이름이기도 하다 */
	public static final String TOP_STORIES = "top_stories";

	/** 헤드라인 칸 — 원소 배열. 사용자 구획에서 헤드라인 항목의 칸 이름이기도 하다 */
	public static final String HEADLINES = "headlines";

	/** 헤드라인 원소가 가리키는 항목 별칭 */
	public static final String REF = "ref";

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

	private static final OutputShape.Arr HEADLINE_ELEMENTS = new OutputShape.Arr(new OutputShape.Obj(List.of(
			new OutputShape.Property(REF, new OutputShape.Str(MAX_ALIAS, OnBlank.KEEP, OverLimit.DROP_ITEM), true, false),
			new OutputShape.Property(TITLE, ITEM_TITLE, true, false),
			new OutputShape.Property(NOTE, new OutputShape.Str(MAX_NOTE, OnBlank.ABSENT, OverLimit.DROP_VALUE), false,
					true))));

	/** 항목이 없는 칸의 서술 — 키 없는 빈 객체 */
	private static final OutputShape.Obj EMPTY = new OutputShape.Obj(List.of());

	private DigestOutputSchema() {
	}

	/**
	 * 이번 요청의 출력 서술 — 개요, 주요 뉴스 항목 별칭(항목 순서)을 키로 한 객체(주요 뉴스 항목이 없으면 빈 객체), 헤드라인 원소 배열(헤드라인
	 * 항목이 없어도 배열)
	 */
	public static OutputShape.Obj root(CandidateSet set) {
		Objects.requireNonNull(set, "set");
		return new OutputShape.Obj(List.of(
				new OutputShape.Property(OVERVIEW, new OutputShape.Str(MAX_OVERVIEW, OnBlank.UNREADABLE, OverLimit.UNREADABLE),
						true, false),
				new OutputShape.Property(TOP_STORIES, topStories(set), true, false),
				new OutputShape.Property(HEADLINES, HEADLINE_ELEMENTS, true, false)));
	}

	private static OutputShape topStories(CandidateSet set) {
		List<String> aliases = set.items()
			.stream()
			.filter(item -> item.kind() == ItemKind.TOP_STORY)
			.map(AliasedItem::alias)
			.toList();
		return aliases.isEmpty() ? EMPTY : new OutputShape.Keyed(aliases, STORY);
	}

}
