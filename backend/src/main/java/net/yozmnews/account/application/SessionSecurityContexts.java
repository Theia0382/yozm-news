package net.yozmnews.account.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * 세션의 보안 문맥 저장소 — 세션에 담긴 보안 문맥을 읽지 못하면(배포로 직렬화 형식이 바뀐 세션 등) 그 세션을 지우고 익명으로 둔다.
 * docs/specs/user.md §4 · user-R7
 * <p>
 * 읽지 못한 세션을 그대로 두면 세션 체인 · 운영자 체인을 지나는 모든 요청(로그인 포함)이 그 세션이 만료될 때까지 실패한다. 세션 ID · 내용은
 * 로그에 싣지 않는다. 세션에는 세션 사용자 인증만 저장한다({@link #saveContext}).
 */
final class SessionSecurityContexts implements SecurityContextRepository {

	private static final Logger logger = LoggerFactory.getLogger(SessionSecurityContexts.class);

	private final HttpSessionSecurityContextRepository delegate = new HttpSessionSecurityContextRepository();

	private final SecurityContextHolderStrategy contexts = SecurityContextHolder.getContextHolderStrategy();

	private final AuthenticationTrustResolver trust = new AuthenticationTrustResolverImpl();

	@Override
	public DeferredSecurityContext loadDeferredContext(HttpServletRequest request) {
		DeferredSecurityContext stored = this.delegate.loadDeferredContext(request);
		return new DeferredSecurityContext() {

			private boolean unreadable;

			@Override
			public SecurityContext get() {
				try {
					return stored.get();
				}
				catch (ConversionFailedException ex) {
					this.unreadable = true;
					discard(request);
					return SessionSecurityContexts.this.contexts.createEmptyContext();
				}
			}

			@Override
			public boolean isGenerated() {
				get();
				return this.unreadable || stored.isGenerated();
			}

		};
	}

	/**
	 * @deprecated 세션 체인 · 운영자 체인은 지연 읽기({@link #loadDeferredContext})만 쓴다
	 */
	@Override
	@Deprecated
	public SecurityContext loadContext(HttpRequestResponseHolder holder) {
		return loadDeferredContext(holder.getRequest()).get();
	}

	/**
	 * 보안 문맥을 세션에 저장한다 — 인증이 없거나 익명이거나 세션 사용자 인증({@link AccountAuthentication})이면 맡기고(익명은 맡긴
	 * 저장소가 세션에 쓰지 않는다), 그 밖의 인증(운영자 토큰 인증)은 저장하지 않는다(세션을 만들지 않는다). 세션에는 사용자 ID 만
	 * 담는다(user-R7).
	 * <p>
	 * 세션 생성 정책을 정한 체인에는 세션 관리 필터가 서서, 세션에 문맥이 없는 인증된 요청의 인증을 이 저장소로 저장하려 한다 — 운영자 체인의 맞는
	 * Bearer 요청이 운영자 인증을 담은 세션을 만들지 않게 여기서 거른다.
	 */
	@Override
	public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = context.getAuthentication();
		if (authentication == null || this.trust.isAnonymous(authentication)
				|| authentication instanceof AccountAuthentication) {
			this.delegate.saveContext(context, request, response);
		}
	}

	@Override
	public boolean containsContext(HttpServletRequest request) {
		try {
			return this.delegate.containsContext(request);
		}
		catch (ConversionFailedException ex) {
			discard(request);
			return false;
		}
	}

	private static void discard(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		logger.warn("세션의 보안 문맥을 읽지 못해 그 세션을 지웠다 — 사용자는 다시 로그인한다");
	}

}
