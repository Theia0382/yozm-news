package net.yozmnews.evaluation;

/**
 * 활성 체계 조회 — 평가 · 후보 선별이 쓴다 — docs/specs/evaluation.md §3.
 * <p>
 * 구현은 {@code application} 의 {@code TagSetDirectoryService} 다. 활성 버전은 언제나 하나다(evaluation-R3).
 */
public interface TagSetDirectory {

	/**
	 * 활성 버전의 내용을 돌려준다.
	 * @throws IllegalStateException 활성 버전이 없을 때 — 첫 기동 적재의 불변식이 깨진 것이다
	 */
	TagSetView activeTagSet();

	/**
	 * 활성 버전의 주제 · 지역 두 기준을 돌려준다.
	 * @throws IllegalStateException 활성 버전이 없을 때
	 */
	TagSetThresholds activeThresholds();

}
