package net.yozmnews.user.application;

/**
 * 기동 단계 — 기존 계정 채우기가 도는 {@code SmartLifecycle} 단계의 정본 — docs/specs/user.md §5 · user-R44
 * <p>
 * 모든 {@code SmartInitializingSingleton}(태그 체계의 첫 기동 적재 — tag-R2 포함)은 그보다 먼저 끝나고, 단계는 웹 서버 시작 단계
 * ({@code Integer.MAX_VALUE - 2048})보다 작아 웹 서버가 요청을 받기 전에 끝난다. 작업 서버(JobRunr)는 {@code ApplicationReadyEvent} 때
 * 시작하므로 그 뒤다.
 */
final class UserStartup {

	/** 기동 단계 — 웹 서버 시작 단계보다 먼저 */
	static final int PHASE = Integer.MAX_VALUE - 4096;

	private UserStartup() {
	}

}
