package net.yozmnews.account.adapter.out.local;

import net.yozmnews.account.domain.guard.ClientAddress;
import net.yozmnews.account.domain.humancheck.HumanCheckResult;
import net.yozmnews.account.domain.humancheck.HumanCheckToken;
import net.yozmnews.account.domain.humancheck.HumanVerifier;
import net.yozmnews.platform.GuardedAction;

/**
 * 로컬 개발의 봇 차단 검증기 — 어떤 토큰에도 통과한다. 화면은 Turnstile 공식 시험 사이트 키로 실제 위젯을 띄운다. docs/specs/account.md §7 ·
 * account-R33 · platform-R18
 */
final class AlwaysPassHumanVerifier implements HumanVerifier {

	@Override
	public HumanCheckResult verify(HumanCheckToken token, GuardedAction action, ClientAddress client) {
		return new HumanCheckResult.Passed();
	}

}
