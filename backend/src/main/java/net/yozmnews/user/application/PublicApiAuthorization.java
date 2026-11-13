package net.yozmnews.user.application;

import java.util.Objects;
import java.util.function.Supplier;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import net.yozmnews.platform.AllowedBeforePasswordChange;
import net.yozmnews.platform.PublicApi;
import net.yozmnews.platform.PublicApis;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.RequestPath;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * 세션 체인의 인가 — 요청의 처리기에 {@link PublicApi} 가 있으면 세션 없이 열고, 처리기가 없으면 열고(404 · 405 · 415 는 MVC 가 낸다), 그
 * 밖은 세션 사용자를 요구한다. 비밀번호를 바꿔야 하는 세션 사용자에게는 {@link AllowedBeforePasswordChange} 가 있는 처리기만 연다 — 거부는
 * 인증된 사용자의 거부라 세션 체인의 거부 처리기가 403 으로 낸다. docs/specs/user.md §4 · user-R5
 * <p>
 * 체인은 {@code DispatcherServlet} 앞이라 처리기 매핑에 묻기 전에 요청 경로를 파싱해 두고, 물은 뒤 원래 상태로 돌린다.
 */
final class PublicApiAuthorization implements AuthorizationManager<RequestAuthorizationContext> {

	private final RequestMappingHandlerMapping mapping;

	PublicApiAuthorization(RequestMappingHandlerMapping mapping) {
		this.mapping = Objects.requireNonNull(mapping, "mapping");
	}

	@Override
	public AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication,
			RequestAuthorizationContext context) {
		@Nullable HandlerMethod handler = handler(context.getRequest());
		if (handler == null || PublicApis.isPublic(handler)) {
			return new AuthorizationDecision(true);
		}
		if (!(authentication.get() instanceof AccountAuthentication account && account.isAuthenticated())) {
			return new AuthorizationDecision(false);
		}
		return new AuthorizationDecision(
				!account.mustChangePassword() || PublicApis.allowedBeforePasswordChange(handler));
	}

	private @Nullable HandlerMethod handler(HttpServletRequest request) {
		boolean parsed = ServletRequestPathUtils.hasParsedRequestPath(request);
		@Nullable RequestPath previous = parsed ? ServletRequestPathUtils.getParsedRequestPath(request) : null;
		if (!parsed) {
			ServletRequestPathUtils.parseAndCache(request);
		}
		try {
			@Nullable HandlerExecutionChain chain = this.mapping.getHandler(request);
			return (chain != null && chain.getHandler() instanceof HandlerMethod method) ? method : null;
		}
		catch (ServletException ex) {
			// 메서드 · 미디어 타입 · 질의 조건이 맞지 않으면 매핑이 ServletException 을 낸다 — 처리기가 없는 요청이다
			return null;
		}
		catch (RuntimeException ex) {
			// 그 밖의 실패는 열지 않는다 — 공개 여부를 판정하지 못한 요청이다
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalStateException("요청의 처리기를 찾지 못해 공개 여부를 판정하지 못했다", ex);
		}
		finally {
			ServletRequestPathUtils.setParsedRequestPath(previous, request);
		}
	}

}
