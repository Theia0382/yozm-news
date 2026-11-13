package net.yozmnews.user.adapter.in.web;

import net.yozmnews.user.UserId;
import net.yozmnews.user.application.AccountAuthentication;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 처리기 인자 {@link UserId} 를 요청의 세션 사용자로 채운다 — 묶기 애너테이션이 없는 인자만 받는다. docs/specs/user.md §4 · user-R6
 * <p>
 * 세션 체인이 세션 사용자 없는 요청을 먼저 막으므로, 세션 사용자가 없는데 여기 오면 결선 오류다.
 */
final class SessionUserArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType() == UserId.class && parameter.getParameterAnnotations().length == 0;
	}

	@Override
	public UserId resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer container,
			NativeWebRequest request, @Nullable WebDataBinderFactory binderFactory) {
		Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
		if (authentication instanceof AccountAuthentication account && account.isAuthenticated()) {
			return account.userId();
		}
		throw new IllegalStateException("세션 사용자가 없는 요청이 사용자 인자를 받는 처리기에 왔다 — 공개 표시를 확인한다: "
				+ parameter.getExecutable().toGenericString());
	}

}
