package net.yozmnews.user.domain.summary;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.platform.OutputShape;
import net.yozmnews.platform.OutputShape.OnBlank;
import net.yozmnews.platform.OutputShape.OverLimit;

/**
 * 정리 출력의 서술 — 읽기({@link SummaryOutputReader})와 생성에 넘길 JSON Schema 문서가 이 서술 하나를 따른다. docs/specs/user.md §5
 * <p>
 * 흐름 전체의 글({@code whole})은 언제나 있고, 꼭대기가 둘 이상이면 꼭대기 별칭을 키로 한 필수 칸({@code tops})이 더 있다. 꼭대기가 하나면 흐름
 * 전체의 글이 곧 그 꼭대기의 글이다. 글이 상한({@link SummaryRules#MAX_TITLE} 등)을 넘으면 상한까지만 남긴다.
 */
public final class SummaryOutputSchema {

	/** 흐름 전체의 글 칸 */
	public static final String WHOLE = "whole";

	/** 꼭대기마다의 글 칸 — 별칭이 키다 */
	public static final String TOPS = "tops";

	/** 글 한 벌의 제목 칸 */
	public static final String TITLE = "title";

	/** 글 한 벌의 흐름 요약 칸 */
	public static final String SUMMARY = "summary";

	/** 글 한 벌의 최근 전개 칸 */
	public static final String RECENT = "recent";

	/** 꼭대기 별칭의 머리 — 뒤에 1 부터의 번호가 붙는다 */
	public static final String ALIAS_PREFIX = "T";

	private static final OutputShape.Obj TEXT = new OutputShape.Obj(List.of(
			new OutputShape.Property(TITLE,
					new OutputShape.Str(SummaryRules.MAX_TITLE, OnBlank.UNREADABLE, OverLimit.TRUNCATE), true, false),
			new OutputShape.Property(SUMMARY,
					new OutputShape.Str(SummaryRules.MAX_SUMMARY, OnBlank.UNREADABLE, OverLimit.TRUNCATE), true, false),
			new OutputShape.Property(RECENT,
					new OutputShape.Str(SummaryRules.MAX_RECENT, OnBlank.UNREADABLE, OverLimit.TRUNCATE), true, false)));

	private SummaryOutputSchema() {
	}

	/** 꼭대기 수만큼의 별칭 — {@code T1}, {@code T2} … */
	public static List<String> aliases(int tops) {
		List<String> aliases = new ArrayList<>();
		for (int i = 1; i <= tops; i++) {
			aliases.add(ALIAS_PREFIX + i);
		}
		return List.copyOf(aliases);
	}

	/**
	 * @param aliases 꼭대기 별칭 — 하나 이상
	 * @return 그 꼭대기들의 정리 출력의 서술
	 */
	public static OutputShape.Obj shape(List<String> aliases) {
		if (aliases.isEmpty()) {
			throw new IllegalArgumentException("꼭대기 별칭이 없다");
		}
		List<OutputShape.Property> properties = new ArrayList<>();
		properties.add(new OutputShape.Property(WHOLE, TEXT, true, false));
		if (aliases.size() > 1) {
			properties.add(new OutputShape.Property(TOPS, new OutputShape.Keyed(aliases, TEXT), true, false));
		}
		return new OutputShape.Obj(properties);
	}

}
