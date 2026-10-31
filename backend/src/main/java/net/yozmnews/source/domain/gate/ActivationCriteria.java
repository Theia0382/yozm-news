package net.yozmnews.source.domain.gate;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.source.Criterion;

/**
 * 활성화 기준 — 정본 · docs/specs/source.md §4 · source-R9.
 * <p>
 * 값을 정한 근거는 ADR-0041 이다. 비율은 분모가 0 이면 판정하지 않는다.
 */
public final class ActivationCriteria {

	/** 기사 수 하한 */
	public static final int MIN_ARTICLES = 1;

	/** 항목 수 대비 건너뛴 항목 비율 상한 */
	public static final double MAX_SKIPPED_RATIO = 0.10;

	/** 기사 수 대비 발행 시각을 해석한 기사 비율 하한 — 프로필이 발행 시각을 읽지 않는다고 선언하면 보지 않는다 */
	public static final double MIN_DATED_RATIO = 0.90;

	/** 읽은 문자 수 대비 U+FFFD 로 바꾼 자리 비율 상한 */
	public static final double MAX_REPLACED_RATIO = 0.001;

	private ActivationCriteria() {
	}

	/**
	 * 미리보기를 기준에 견준다.
	 * @return 못 미친 기준 — {@link Criterion} 순서. 비었으면 통과다
	 */
	public static List<Shortfall> judge(FeedPreview preview) {
		List<Shortfall> shortfalls = new ArrayList<>();
		if (preview.articles() < MIN_ARTICLES) {
			shortfalls.add(new Shortfall(Criterion.ARTICLES, preview.articles(), MIN_ARTICLES));
		}
		if (preview.items() > 0) {
			double skipped = (double) preview.skipped().size() / preview.items();
			if (skipped > MAX_SKIPPED_RATIO) {
				shortfalls.add(new Shortfall(Criterion.SKIPPED_ITEMS, skipped, MAX_SKIPPED_RATIO));
			}
		}
		if (preview.articles() > 0 && !preview.publishedAtUnread()) {
			double dated = (double) preview.datedArticles() / preview.articles();
			if (dated < MIN_DATED_RATIO) {
				shortfalls.add(new Shortfall(Criterion.DATED_ARTICLES, dated, MIN_DATED_RATIO));
			}
		}
		if (preview.decodedCharacters() > 0) {
			double replaced = (double) preview.replacedCharacters() / preview.decodedCharacters();
			if (replaced > MAX_REPLACED_RATIO) {
				shortfalls.add(new Shortfall(Criterion.REPLACED_CHARACTERS, replaced, MAX_REPLACED_RATIO));
			}
		}
		return List.copyOf(shortfalls);
	}

}
