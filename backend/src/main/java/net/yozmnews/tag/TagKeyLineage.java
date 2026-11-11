package net.yozmnews.tag;

/**
 * 태그 키 계보 조회 — 옛 버전의 키를 활성 버전의 키로 푼다. 후보 선별 · 주제별 시계열 · 사용자 선호가 쓴다 —
 * docs/specs/tag.md §3 · tag-R26.
 * <p>
 * 구현은 {@code application} 의 {@code TagKeyLineageService} 다. 트랜잭션을 열지 않는다.
 */
public interface TagKeyLineage {

	/**
	 * 버전의 모든 키를 활성 버전 키의 집합으로 옮긴 표를 돌려준다 — 그 버전이 활성이면 항등 표다.
	 * @throws IllegalArgumentException 없는 버전일 때
	 */
	TagKeyMapping toActive(TagSetId from);

}
