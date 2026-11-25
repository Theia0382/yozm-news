package net.yozmnews.platform;

/**
 * 운영 설정을 정한 행위자의 종류 — 태그 기준 · 저장 기준 · 태그 체계 버전(tag-R13), 스토리라인의 큰 판정 기준(storyline-R20), 가입
 * 정책(account-R25)이 이력에 남긴다 · docs/specs/platform.md §3.
 * <p>
 * 사용자를 가리키지 않는다 — 운영자 계정 세션과 운영자 토큰을 가리지 않고 {@link #OPERATOR} 하나로 남긴다.
 */
public enum ChangeActor {

	/** 첫 기동 적재 */
	SYSTEM,

	/** 공개 운영 API 를 거친 운영자 */
	OPERATOR

}
