package net.yozmnews.user.application;

/**
 * 계정 쪽 기동 단계 — 첫 운영자가 도는 {@code SmartLifecycle} 단계의 정본. docs/specs/account.md §5 · account-R27
 * <p>
 * 웹 서버 시작 단계({@code Integer.MAX_VALUE - 2048})보다 작아 웹 서버가 요청을 받기 전에 끝난다. 첫 운영자는 다른 모듈의 데이터를 읽지 않아
 * 다른 기동 작업과 순서가 없다.
 */
final class AccountStartup {

	/** 기동 단계 — 웹 서버 시작 단계보다 먼저 */
	static final int PHASE = Integer.MAX_VALUE - 4096;

	private AccountStartup() {
	}

}
