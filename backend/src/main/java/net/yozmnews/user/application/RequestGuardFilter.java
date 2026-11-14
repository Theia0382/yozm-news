package net.yozmnews.user.application;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.yozmnews.platform.GuardedAction;
import net.yozmnews.platform.HandlerGuards;
import net.yozmnews.platform.ProblemResponses;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.guard.LimitDecision;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;

/**
 * 요청 방어 — 처리기에 {@link net.yozmnews.platform.RequestLimited} 가 있으면 처리기 앞에서 그 동작의 한도로 요청을 세고, 넘었으면 429
 * {@code rate-limited} 와 {@code Retry-After}(초)로 끝낸다. docs/specs/user.md §4 · user-R40
 * <p>
 * 세션 체인의 인가 뒤에 둔다 — 세션 사용자를 알고, 앞선 필터 · 인가가 거부한 요청(요청 보호 토큰 없음 · 세션 없음 등)은 세지 않는다.
 * 세션 체인 안에서만 만든다(빈이 아니다 — 빈이면 서블릿 필터로도 등록돼 모든 요청에 돈다).
 */
final class RequestGuardFilter extends OncePerRequestFilter {

	private final HandlerLookup handlers;

	private final RequestLimiter limiter;

	private final ProblemResponses problems;

	RequestGuardFilter(HandlerLookup handlers, RequestLimiter limiter, ProblemResponses problems) {
		this.handlers = Objects.requireNonNull(handlers, "handlers");
		this.limiter = Objects.requireNonNull(limiter, "limiter");
		this.problems = Objects.requireNonNull(problems, "problems");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		@Nullable HandlerMethod handler = this.handlers.find(request);
		Optional<GuardedAction> limited = (handler == null) ? Optional.empty() : HandlerGuards.requestLimited(handler);
		if (limited.isPresent()) {
			LimitDecision decision = this.limiter.consume(limited.get(), ClientAddresses.of(request), sessionUser());
			if (decision instanceof LimitDecision.Exceeded exceeded) {
				response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(this.limiter.retryAfterSeconds(exceeded)));
				this.problems.write(response,
						this.problems.problem(ProblemTypes.RATE_LIMITED, ProblemTypes.RATE_LIMITED_TITLE,
								HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많다 — Retry-After 초 뒤에 다시 시도한다", request));
				return;
			}
		}
		chain.doFilter(request, response);
	}

	private static @Nullable UserId sessionUser() {
		Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
		return (authentication instanceof AccountAuthentication account && account.isAuthenticated()) ? account.userId()
				: null;
	}

}
