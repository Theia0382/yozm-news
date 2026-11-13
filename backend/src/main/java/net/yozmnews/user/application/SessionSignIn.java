package net.yozmnews.user.application;

import java.util.List;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.session.SessionLifetime;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;

/**
 * 세션 로그인 · 로그아웃 — docs/specs/user.md §4 · user-R8 · user-R11
 * <p>
 * 로그인은 세션 ID 와 CSRF 토큰을 바꾸고(세션 인증 전략은 명시적으로 불러야 돈다), 보안 문맥(사용자 ID 만)을 세션에 저장하고, 로그인 유지에 맞는
 * 비활성 기간을 세션에 정한다. 로그아웃은 세션을 지우고(세션 쿠키가 만료된다) CSRF 토큰을 지운다.
 */
public final class SessionSignIn {

	private final SecurityContextRepository contextRepository;

	private final CsrfTokenRepository csrfTokens;

	private final SessionAuthenticationStrategy strategy;

	private final SecurityContextHolderStrategy contexts = SecurityContextHolder.getContextHolderStrategy();

	SessionSignIn(SecurityContextRepository contextRepository, CsrfTokenRepository csrfTokens) {
		this.contextRepository = Objects.requireNonNull(contextRepository, "contextRepository");
		this.csrfTokens = Objects.requireNonNull(csrfTokens, "csrfTokens");
		CsrfAuthenticationStrategy csrf = new CsrfAuthenticationStrategy(csrfTokens);
		csrf.setRequestHandler(eagerCsrfTokens());
		this.strategy = new CompositeSessionAuthenticationStrategy(
				List.of(new ChangeSessionIdAuthenticationStrategy(), csrf));
	}

	/**
	 * 바꾼 CSRF 토큰을 지연하지 않고 바로 만들어 이 응답의 쿠키에 싣는다 — 지연하면 옛 쿠키만 지워져, 로그인 직후의 변경 요청이 토큰 없이
	 * 거부된다(user-R11)
	 */
	private static XorCsrfTokenRequestAttributeHandler eagerCsrfTokens() {
		XorCsrfTokenRequestAttributeHandler handler = new XorCsrfTokenRequestAttributeHandler();
		handler.setCsrfRequestAttributeName(null);
		return handler;
	}

	/** 세션 사용자로 로그인한다 — 로그인 유지면 지속 쿠키 · 비활성 7일, 아니면 브라우저 세션 쿠키 · 비활성 2시간 */
	public void signIn(UserId userId, boolean keep, HttpServletRequest request, HttpServletResponse response) {
		AccountAuthentication authentication = AccountAuthentication.stored(userId);
		this.strategy.onAuthentication(authentication, request, response);
		SecurityContext context = this.contexts.createEmptyContext();
		context.setAuthentication(authentication);
		this.contexts.setContext(context);
		this.contextRepository.saveContext(context, request, response);
		if (keep) {
			request.setAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR, true);
		}
		request.getSession().setMaxInactiveInterval((int) SessionLifetime.of(keep).toSeconds());
	}

	/**
	 * 이 요청이 들고 온 세션을 버리고 새 세션으로 로그인한다 — 계정을 새로 만든 가입 · 그 계정의 세션을 모두 지운 복구 재설정 뒤. 들고 온 세션이
	 * 다른 계정의 것이어도, 이미 저장소에서 지워진 것이어도 새 세션이 선다(user-R11)
	 */
	public void signInAfresh(UserId userId, boolean keep, HttpServletRequest request, HttpServletResponse response) {
		HttpSession carried = request.getSession(false);
		if (carried != null) {
			carried.invalidate();
		}
		signIn(userId, keep, request, response);
	}

	/**
	 * 로그인한 세션의 ID 와 CSRF 토큰을 바꾼다 — 비밀번호를 바꾼 뒤(세션 고정 방지). 세션의 내용 · 수명은 그대로이고, 바뀐 ID 는 요청 끝에서
	 * 저장된다(user-R11)
	 */
	public void renew(HttpServletRequest request, HttpServletResponse response) {
		this.strategy.onAuthentication(this.contexts.getContext().getAuthentication(), request, response);
	}

	/** 로그아웃한다 — 세션이 없어도 같은 결과다 */
	public void signOut(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		this.contexts.clearContext();
		this.csrfTokens.saveToken(null, request, response);
	}

}
