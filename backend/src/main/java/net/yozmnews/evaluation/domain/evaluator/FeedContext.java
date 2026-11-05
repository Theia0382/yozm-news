package net.yozmnews.evaluation.domain.evaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.yozmnews.platform.CodePoints;
import org.jspecify.annotations.Nullable;

/**
 * 기사가 실린 피드 하나의 맥락(출처 이름 · 피드 섹션 · RSS 항목 카테고리) — docs/specs/evaluation.md §4 · evaluation-R17.
 *
 * @param sourceName 출처 이름 — 없으면 null
 * @param section 피드 섹션(수집 프로필) — 없으면 null
 * @param categories 그 피드 항목의 RSS 카테고리 — 피드 순서
 */
public record FeedContext(@Nullable String sourceName, @Nullable String section, List<String> categories) {

	/** 피드 하나에서 담는 카테고리 수의 상한 — 정본 */
	public static final int MAX_CATEGORIES = 5;

	/** 카테고리 하나의 길이 상한(코드 포인트) — 정본 */
	public static final int MAX_CATEGORY_CODE_POINTS = 40;

	/**
	 * @throws IllegalArgumentException 이름 · 섹션 · 카테고리가 공백뿐이거나 카테고리가 상한을 넘을 때 — 상한을 모르는 입력은
	 * {@link #limited} 로 만든다
	 */
	public FeedContext {
		if (sourceName != null && sourceName.isBlank()) {
			throw new IllegalArgumentException("출처 이름이 공백뿐이다 — 없으면 null 이다");
		}
		if (section != null && section.isBlank()) {
			throw new IllegalArgumentException("섹션이 공백뿐이다 — 없으면 null 이다");
		}
		categories = List.copyOf(categories);
		if (categories.size() > MAX_CATEGORIES) {
			throw new IllegalArgumentException("카테고리는 " + MAX_CATEGORIES + "개까지다: " + categories.size());
		}
		for (String category : categories) {
			if (category.isBlank()) {
				throw new IllegalArgumentException("카테고리가 공백뿐이다");
			}
			if (category.codePointCount(0, category.length()) > MAX_CATEGORY_CODE_POINTS) {
				throw new IllegalArgumentException("카테고리는 " + MAX_CATEGORY_CODE_POINTS + " 코드 포인트까지다");
			}
		}
	}

	/**
	 * 상한을 적용해 만든다 — 정본. 카테고리마다 앞 {@value #MAX_CATEGORY_CODE_POINTS} 코드 포인트로 자르고 끝 공백을
	 * 지운 뒤, 공백뿐인 카테고리를 버리고 남은 것의 앞에서부터 {@value #MAX_CATEGORIES}개를 담는다. 공백뿐인 이름 · 섹션은 null 로 둔다.
	 */
	public static FeedContext limited(@Nullable String sourceName, @Nullable String section, List<String> categories) {
		List<String> kept = new ArrayList<>();
		for (String category : categories) {
			if (kept.size() == MAX_CATEGORIES) {
				break;
			}
			String cut = CodePoints.prefix(Objects.requireNonNull(category, "category"), MAX_CATEGORY_CODE_POINTS)
				.stripTrailing();
			if (!cut.isBlank()) {
				kept.add(cut);
			}
		}
		return new FeedContext(blankToNull(sourceName), blankToNull(section), kept);
	}

	private static @Nullable String blankToNull(@Nullable String text) {
		return (text == null || text.isBlank()) ? null : text;
	}

}
