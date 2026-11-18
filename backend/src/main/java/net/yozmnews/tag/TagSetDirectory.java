package net.yozmnews.tag;

/**
 * 활성 체계 · 버전 내용 조회 — 평가 · 후보 선별 · 사용자 선호가 쓴다 — docs/specs/tag.md §3.
 * <p>
 * 구현은 {@code application} 의 {@code TagSetDirectoryService} 다. 활성 버전은 언제나 하나다(tag-R3). 트랜잭션을 열지 않는다 — 각 조회는
 * 그 순간의 값이다.
 */
public interface TagSetDirectory {

	/**
	 * 활성 버전의 내용을 돌려준다.
	 * @throws IllegalStateException 활성 버전이 없을 때 — 첫 기동 적재의 불변식이 깨진 것이다
	 */
	TagSetView activeTagSet();

	/**
	 * 활성 버전의 주제 · 지역 태그 기준 · 저장 기준을 돌려준다.
	 * @throws IllegalStateException 활성 버전이 없을 때
	 */
	TagSetThresholds activeThresholds();

	/**
	 * 버전 하나의 내용을 돌려준다 — 활성이 아닌 버전도 낸다. 버전 내용은 바뀌지 않으므로(tag-R4) 같은 ID 는 언제나 같은 내용이다.
	 * @throws IllegalArgumentException 없는 버전일 때
	 */
	TagSetView tagSet(TagSetId id);

	/**
	 * 버전 하나의 키와 이름을 돌려준다 — {@link #tagSet} 과 같은 버전 내용에서 이름만 낸다.
	 * @throws IllegalArgumentException 없는 버전일 때
	 */
	TagSetNames names(TagSetId id);

}
