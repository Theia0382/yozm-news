package net.yozmnews.platform.application;

import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer} 의 운영자 토큰을 인증한다 — 맞으면 운영자 인증을 이 요청의 보안 문맥에 둔다.
 * <p>
 * 틀리거나 없으면 아무것도 두지 않고 다음으로 넘긴다 — 인가가 거부하고 진입점이 응답한다. 운영자 체인 안에서만 만든다(빈이 아니다 — 빈이면
 * 서블릿 필터로도 등록돼 모든 요청에 돈다). 토큰 · 헤더 값을 로그에 쓰지 않는다.
 */
final class AdminTokenFilter extends OncePerRequestFilter {

	private static final Pattern BEARER = Pattern.compile("(?i)Bearer +(\\S+) *");

	private final AuthenticationProvider provider;

	private final SecurityContextHolderStrategy contexts = SecurityContextHolder.getContextHolderStrategy();

	AdminTokenFilter(AuthenticationProvider provider) {
		this.provider = Objects.requireNonNull(provider, "provider");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		@Nullable String token = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
		if (token != null) {
			try {
				SecurityContext context = this.contexts.createEmptyContext();
				context.setAuthentication(this.provider.authenticate(AdminTokenAuthentication.presented(token)));
				this.contexts.setContext(context);
			}
			catch (AuthenticationException ex) {
				this.contexts.clearContext();
			}
		}
		chain.doFilter(request, response);
	}

	/** {@code Bearer} 스킴(대소문자 무시)의 토큰 — 스킴이 다르거나 형식이 틀리면 null */
	static @Nullable String bearerToken(@Nullable String header) {
		if (header == null) {
			return null;
		}
		Matcher matcher = BEARER.matcher(header);
		return matcher.matches() ? matcher.group(1) : null;
	}

}
