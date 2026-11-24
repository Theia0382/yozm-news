package net.yozmnews.account.application;

import java.util.Objects;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import net.yozmnews.account.domain.session.HandlerMarks;
import net.yozmnews.account.domain.session.SessionAccess;
import net.yozmnews.credential.LlmKeys;
import net.yozmnews.platform.AllowedBeforePasswordChange;
import net.yozmnews.platform.AllowedWithoutLlmKey;
import net.yozmnews.platform.PublicApi;
import net.yozmnews.platform.PublicApis;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.method.HandlerMethod;

/**
 * 세션 체인의 인가 — 요청의 처리기에 {@link PublicApi} 가 있으면 세션 없이 열고, 처리기가 없으면 열고(404 · 405 · 415 는 MVC 가 낸다), 그
 * 밖은 세션 사용자를 요구한다. 비밀번호를 바꿔야 하는 세션 사용자에게는 {@link AllowedBeforePasswordChange} 가 있는 처리기만, LLM 키가 없는
 * 세션 사용자에게는 {@link AllowedWithoutLlmKey} 가 있는 처리기만 연다 — 판정은 {@link SessionAccess} 가 하고, 거부면 판정 값을 요청
 * 속성({@link #DENIAL})에 둬 세션 체인의 거부 처리기가 문제 유형을 가른다. 처리기는 {@link HandlerLookup} 이 찾는다. docs/specs/user.md §4 ·
 * user-R5
 */
final class PublicApiAuthorization implements AuthorizationManager<RequestAuthorizationContext> {

	/** 거부한 요청에 판정 값({@link SessionAccess})을 두는 요청 속성의 이름 */
	static final String DENIAL = SessionAccess.class.getName();

	private final HandlerLookup handlers;

	private final LlmKeys llmKeys;

	PublicApiAuthorization(HandlerLookup handlers, LlmKeys llmKeys) {
		this.handlers = Objects.requireNonNull(handlers, "handlers");
		this.llmKeys = Objects.requireNonNull(llmKeys, "llmKeys");
	}

	@Override
	public AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication,
			RequestAuthorizationContext context) {
		HttpServletRequest request = context.getRequest();
		@Nullable HandlerMethod handler = this.handlers.find(request);
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

}
