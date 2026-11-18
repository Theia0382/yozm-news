package net.yozmnews.digest;

/**
 * 다이제스트 섹션의 종류 — 공개 타입 · docs/specs/digest.md §3 · digest-R7
 */
public enum SectionKind {

	/** 오늘의 흐름 — 인용 없음 */
	OVERVIEW,

	/** 주요 뉴스 하나 */
	TOP_STORY,

	/** 추가 헤드라인 묶음 */
	MORE_HEADLINES;

	/** 기사를 인용하는 섹션인가 */
	public boolean cites() {
		return switch (this) {
			case OVERVIEW -> false;
			case TOP_STORY, MORE_HEADLINES -> true;
		};
	}

}
