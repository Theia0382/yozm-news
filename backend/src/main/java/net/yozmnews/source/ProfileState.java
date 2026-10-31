package net.yozmnews.source;

/**
 * 소스의 프로필을 얻었는가 — docs/specs/source.md §3
 */
public enum ProfileState {

	/** 얻었다 */
	AVAILABLE,
	/** 참조하던 프리셋이 카탈로그에서 사라졌다 */
	PRESET_MISSING,
	/** 운영자 원문이 지금의 검증기에 걸린다 */
	INVALID

}
