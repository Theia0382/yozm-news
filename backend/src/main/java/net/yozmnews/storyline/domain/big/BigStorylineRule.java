package net.yozmnews.storyline.domain.big;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Objects;

import net.yozmnews.event.EventTally;
import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.BigStorylineCriteria;

/**
 * 큰 판정 규칙의 정본 — docs/specs/storyline.md §4 · storyline-R19.
 * <p>
 * 기간 집계의 서로 다른 소스 수가 소스 기준 이상이고, 중요도 평균이 있고 중요도 기준 이상이면 큰 스토리라인이다(경계 포함). 식을 평가 세트로
 * 정하기 전의 임시 산식이고, 식을 바꾸는 자리는 이 타입 하나다 — 큰 스토리라인 조회 · 미리보기 · 목록의 큰 여부가 모두 이 규칙을 부른다.
 */
public final class BigStorylineRule {

	/** 큰 스토리라인의 순서 — 소스 수 내림차순, 중요도 평균 내림차순, 스토리라인 ID 오름차순 */
	public static final Comparator<BigStoryline> ORDER = Comparator
		.comparingInt((BigStoryline big) -> big.tally().sources())
		.reversed()
		.thenComparing((BigStoryline big) -> big.tally().importance(),
				Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder()))
		.thenComparingLong(big -> big.storyline().id().value());

	private BigStorylineRule() {
	}

	/** 집계가 두 기준에 모두 이르는가 — 경계 포함 */
	public static boolean isBig(EventTally tally, BigStorylineCriteria criteria) {
		Objects.requireNonNull(tally, "tally");
		Objects.requireNonNull(criteria, "criteria");
		BigDecimal importance = tally.importance();
		return tally.sources() >= criteria.minSources() && importance != null
				&& importance.compareTo(criteria.minImportance()) >= 0;
	}

}
