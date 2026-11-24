package net.yozmnews.account.domain.humancheck;

import net.yozmnews.account.domain.guard.ClientAddress;
import net.yozmnews.platform.GuardedAction;

/**
 * 봇 차단 검증 포트 — 사람 확인 토큰을 외부 서비스에 물어 확인한다. 구현은 {@code adapter/out/turnstile} 의 Turnstile 검증기와,
 * 로컬 개발의 항상 통과 검증기({@code adapter/out/local}). docs/specs/user.md §4 · user-R41
 */
public interface HumanVerifier {

	/**
	 * 토큰을 확인한다.
	 * @param action 토큰이 나온 위젯의 동작이어야 하는 것
	 * @param client 클라이언트 주소 — 검증 서비스에 함께 보낸다
	 */
	HumanCheckResult verify(HumanCheckToken token, GuardedAction action, ClientAddress client);

}
