package net.yozmnews.user.application;

import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.yozmnews.platform.ProblemResponses;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 운영자 토큰(스크립트용 Bearer)을 인증한다 — {@code Authorization} 헤더를 낸 요청은 토큰으로만 판정한다. docs/specs/user.md §4 ·
 * user-R1 · user-R2
 * <p>
 * 헤더가 없으면 아무것도 하지 않고 넘긴다(세션 인증으로). {@code Bearer} 토큰이 맞으면 운영자 인증으로 이 요청의 보안 문맥을 덮고 넘긴다.
 * 스킴이 다르거나 형식이 틀리거나 토큰이 맞지 않거나 토큰을 설정하지 않았으면 세션으로 넘어가지 않고 401 {@code admin-unauthorized} 로
 * 끝낸다 — 틀린 Bearer 헤더를 낸 요청이 세션 쿠키로 인증돼 CSRF 면제를 받는 길이 없다. CSRF 필터 앞에 서서 예외 변환 필터를 거치지
 * 않으므로 직접 응답한다.
 * <p>
 * 운영자 체인 안에서만 만든다(빈이 아니다 — 빈이면 서블릿 필터로도 등록돼 모든 요청에 돈다). 토큰 · 헤더 값을 로그 · 응답에 쓰지 않는다.
 */
final class AdminTokenFilter extends OncePerRequestFilter {

	private static final Pattern BEARER = Pattern.compile("(?i)Bearer +(\\S+) *");

	private final AuthenticationProvider provider;

	private final ProblemResponses problems;

	private final SecurityContextHolderStrategy contexts = SecurityContextHolder.getContextHolderStrategy();

	AdminTokenFilter(AuthenticationProvider provider, ProblemResponses problems) {
		this.provider = Objects.requireNonNull(provider, "provider");
		this.problems = Objects.requireNonNull(problems, "problems");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		@Nullable String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null) {
			chain.doFilter(request, response);
			return;
		}
		@Nullable Authentication operator = authenticate(bearerToken(header));
		if (operator != null) {
			SecurityContext context = this.contexts.createEmptyContext();
			context.setAuthentication(operator);
			this.contexts.setContext(context);
			chain.doFilter(request, response);
			return;
		}
		this.contexts.clearContext();
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		this.problems.write(response,
				this.problems.problem(ProblemTypes.ADMIN_UNAUTHORIZED, ProblemTypes.ADMIN_UNAUTHORIZED_TITLE,
						HttpStatus.UNAUTHORIZED, "운영자 토큰(Bearer)이 맞지 않거나 형식이 틀리다", request));
	}

	/** 제시된 토큰의 운영자 인증 — 토큰이 없거나 맞지 않으면 null */
	private @Nullable Authentication authenticate(@Nullable String token) {
		if (token == null) {
			return null;
		}
		try {
			return this.provider.authenticate(AdminTokenAuthentication.presented(token));
		}
		catch (AuthenticationException ex) {
			return null;
		}
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
