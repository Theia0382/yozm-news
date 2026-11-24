package net.yozmnews.account.application;

import java.util.function.Supplier;

import net.yozmnews.account.domain.account.AccountRole;
import net.yozmnews.account.domain.session.AdminAccess;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * 운영자 체인의 인가 — 이 요청의 인증(운영자 토큰 인증 · 세션 사용자 · 익명)으로 {@link AdminAccess} 가 판정하고, 거부면 판정 값을 요청
 * 속성({@link #DENIAL})에 둬 운영자 체인의 거부 처리기가 문제 유형을 가른다. 처리기를 찾지 않는다 — 처리기 없는 운영자 경로도 인증 · 역할
 * 판정이 먼저다. docs/specs/account.md §4 · account-R1
 */
final class AdminAuthorization implements AuthorizationManager<RequestAuthorizationContext> {

	/** 거부한 요청에 판정 값({@link AdminAccess})을 두는 요청 속성의 이름 */
	static final String DENIAL = AdminAccess.class.getName();

	@Override
	public AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication,
			RequestAuthorizationContext context) {
		@Nullable Authentication current = authentication.get();
		AdminAccess access;
		if (AdminTokenAuthentication.isOperator(current)) {
			access = AdminAccess.decide(true, null, false);
		}
		else if (current instanceof AccountAuthentication account && account.isAuthenticated()) {
			access = AdminAccess.decide(false, account.mustChangePassword(), account.hasRole(AccountRole.ADMIN));
		}
		else {
			access = AdminAccess.decide(false, null, false);
		}
		if (access != AdminAccess.OPEN) {
			context.getRequest().setAttribute(DENIAL, access);
		}
		return new AuthorizationDecision(access == AdminAccess.OPEN);
	}

}
