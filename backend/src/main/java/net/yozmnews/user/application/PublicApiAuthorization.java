package net.yozmnews.user.application;

import java.util.Objects;
import java.util.function.Supplier;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import net.yozmnews.credential.LlmKeys;
import net.yozmnews.platform.AllowedBeforePasswordChange;
import net.yozmnews.platform.AllowedWithoutLlmKey;
import net.yozmnews.platform.PublicApi;
import net.yozmnews.platform.PublicApis;
import net.yozmnews.user.domain.session.HandlerMarks;
import net.yozmnews.user.domain.session.SessionAccess;
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
 * 밖은 세션 사용자를 요구한다. 비밀번호를 바꿔야 하는 세션 사용자에게는 {@link AllowedBeforePasswordChange} 가 있는 처리기만, LLM 키가 없는
 * 세션 사용자에게는 {@link AllowedWithoutLlmKey} 가 있는 처리기만 연다 — 판정은 {@link SessionAccess} 가 하고, 거부면 판정 값을 요청
 * 속성({@link #DENIAL})에 둬 세션 체인의 거부 처리기가 문제 유형을 가른다. docs/specs/user.md §4 · user-R5
 * <p>
 * 체인은 {@code DispatcherServlet} 앞이라 처리기 매핑에 묻기 전에 요청 경로를 파싱해 두고, 물은 뒤 원래 상태로 돌린다.
 */
final class PublicApiAuthorization implements AuthorizationManager<RequestAuthorizationContext> {

	/** 거부한 요청에 판정 값({@link SessionAccess})을 두는 요청 속성의 이름 */
	static final String DENIAL = SessionAccess.class.getName();

	private final RequestMappingHandlerMapping mapping;

	private final LlmKeys llmKeys;

	PublicApiAuthorization(RequestMappingHandlerMapping mapping, LlmKeys llmKeys) {
		this.mapping = Objects.requireNonNull(mapping, "mapping");
		this.llmKeys = Objects.requireNonNull(llmKeys, "llmKeys");
	}

	@Override
	public AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication,
			RequestAuthorizationContext context) {
		HttpServletRequest request = context.getRequest();
		@Nullable HandlerMethod handler = handler(request);
		HandlerMarks marks = (handler == null) ? HandlerMarks.NO_HANDLER
				: new HandlerMarks(true, PublicApis.isPublic(handler), PublicApis.allowedBeforePasswordChange(handler),
						PublicApis.allowedWithoutLlmKey(handler));
		// 공개 처리기 · 처리기 없는 요청은 인증을 읽지 않는다 — 세션을 건드리지 않는다
		@Nullable AccountAuthentication account = (!marks.present() || marks.isPublic()) ? null
				: sessionUser(authentication.get());
		SessionAccess access = SessionAccess.decide(marks, (account == null) ? null : account.mustChangePassword(),
				() -> this.llmKeys.present(Objects.requireNonNull(account).userId()));
		if (access != SessionAccess.OPEN) {
			request.setAttribute(DENIAL, access);
		}
		return new AuthorizationDecision(access == SessionAccess.OPEN);
	}

	private static @Nullable AccountAuthentication sessionUser(@Nullable Authentication authentication) {
		return (authentication instanceof AccountAuthentication account && account.isAuthenticated()) ? account : null;
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
