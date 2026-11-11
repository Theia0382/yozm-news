package net.yozmnews.tag;

/**
 * 태그 키 계보의 종류 — docs/specs/tag.md §3 · tag-R22
 */
public enum LineageKind {

	/** 이름 변경 — 이전 키 하나가 새 키 하나로 */
	RENAME,

	/** 분할 — 이전 키 하나가 새 키 여럿으로 */
	SPLIT,

	/** 병합 — 이전 키 여럿이 새 키 하나로 */
	MERGE

}
