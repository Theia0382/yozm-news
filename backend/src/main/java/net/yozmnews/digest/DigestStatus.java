package net.yozmnews.digest;

/**
 * 다이제스트 상태 — 공개 타입 · docs/specs/digest.md §3 · digest-R1
 */
public enum DigestStatus {

	/** 생성을 기다린다 */
	SCHEDULED,

	/** 생성 중이다 */
	RUNNING,

	/** 인용이 있는 다이제스트를 저장했다 */
	READY,

	/** 기간에 후보가 없었다 */
	EMPTY,

	/** 생성이 실패했다 */
	FAILED;

	/** 끝난 상태인가 — {@link #READY} · {@link #EMPTY} · {@link #FAILED} */
	public boolean finished() {
		return switch (this) {
			case SCHEDULED, RUNNING -> false;
			case READY, EMPTY, FAILED -> true;
		};
	}

}
