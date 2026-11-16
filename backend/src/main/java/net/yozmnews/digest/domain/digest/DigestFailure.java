package net.yozmnews.digest.domain.digest;

/**
 * 실패한 다이제스트의 오류 코드 · docs/specs/digest.md §5 · digest-R8
 */
public enum DigestFailure {

	/** 출력을 읽지 못했다 */
	UNREADABLE_OUTPUT,

	/** 검증 뒤 인용이 하나도 남지 않았다 */
	NO_CITATION,

	/** 생성기가 없었다 */
	NO_GENERATOR

}
