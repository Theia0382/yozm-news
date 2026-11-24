package net.yozmnews.account.application;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.yozmnews.account.domain.guard.ClientAddress;
import net.yozmnews.account.domain.guard.LimitDecision;
import net.yozmnews.account.domain.humancheck.HumanCheckResult;
import net.yozmnews.platform.GuardedAction;
import net.yozmnews.platform.HandlerGuards;
import net.yozmnews.platform.ProblemResponses;
import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;

/**
 * 요청 방어 — 처리기 앞에서 요청 제한을 걸고, 그 다음 봇 차단을 건다. docs/specs/account.md §4 · account-R32 · account-R33
 * <ol>
 * <li>처리기에 {@link net.yozmnews.platform.RequestLimited} 가 있으면 그 동작의 한도로 요청을 세고, 넘었으면 429 {@code rate-limited} 와
 * {@code Retry-After}(초)로 끝낸다 — 봇 차단 검증(외부 요청)은 부르지 않는다</li>
 * <li>처리기에 {@link net.yozmnews.platform.HumanChecked} 가 있고 봇 차단을 켰으면 헤더 {@value #HUMAN_CHECK_HEADER} 의 토큰을 확인한다 —
 * 거부면 403 {@code human-check-failed}, 검증 서비스에 닿지 못하면 경고를 남기고 통과시킨다</li>
 * </ol>
 * 세션 체인의 인가 뒤에 둔다 — 세션 사용자를 알고, 앞선 필터 · 인가가 거부한 요청(요청 보호 토큰 없음 · 세션 없음 등)은 세지 않는다.
 * 세션 체인 안에서만 만든다(빈이 아니다 — 빈이면 서블릿 필터로도 등록돼 모든 요청에 돈다).
 */
final class RequestGuardFilter extends OncePerRequestFilter {

	/** 화면이 사람 확인 토큰을 싣는 요청 헤더 */
	static final String HUMAN_CHECK_HEADER = "X-Human-Check";

	private static final Logger logger = LoggerFactory.getLogger(RequestGuardFilter.class);

	private final HandlerLookup handlers;

	private final RequestLimiter limiter;

	private final HumanCheck humanCheck;

	private final ProblemResponses problems;

	RequestGuardFilter(HandlerLookup handlers, RequestLimiter limiter, HumanCheck humanCheck,
			ProblemResponses problems) {
		this.handlers = Objects.requireNonNull(handlers, "handlers");
		this.limiter = Objects.requireNonNull(limiter, "limiter");
		this.humanCheck = Objects.requireNonNull(humanCheck, "humanCheck");
		this.problems = Objects.requireNonNull(problems, "problems");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		@Nullable HandlerMethod handler = this.handlers.find(request);
		if (handler == null) {
			chain.doFilter(request, response);
			return;
		}
		Optional<GuardedAction> limited = HandlerGuards.requestLimited(handler);
		Optional<GuardedAction> checked = HandlerGuards.humanChecked(handler);
		if (limited.isEmpty() && checked.isEmpty()) {
			chain.doFilter(request, response);
			return;
		}
		ClientAddress client = ClientAddresses.of(request);
		if (limited.isPresent()
				&& this.limiter.consume(limited.get(), client, sessionUser()) instanceof LimitDecision.Exceeded exceeded) {
			response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(this.limiter.retryAfterSeconds(exceeded)));
			this.problems.write(response, this.problems.problem(ProblemTypes.RATE_LIMITED, ProblemTypes.RATE_LIMITED_TITLE,
					HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많다 — Retry-After 초 뒤에 다시 시도한다", request));
			return;
		}
		if (checked.isPresent() && this.humanCheck.enabled()) {
			switch (this.humanCheck.verify(request.getHeader(HUMAN_CHECK_HEADER), checked.get(), client)) {
				case HumanCheckResult.Passed passed -> {
				}
				case HumanCheckResult.Rejected rejected -> {
					logger.info("사람 확인을 거부했다 — 동작 {} · 까닭 {}", checked.get(), rejected.reason());
					this.problems.write(response,
							this.problems.problem(ProblemTypes.HUMAN_CHECK_FAILED,
									ProblemTypes.HUMAN_CHECK_FAILED_TITLE, HttpStatus.FORBIDDEN,
									"사람 확인 토큰(" + HUMAN_CHECK_HEADER + ")이 없거나 확인되지 않았다 — 위젯을 다시 받아 제출한다", request));
					return;
				}
				case HumanCheckResult.Unavailable unavailable -> logger
					.warn("봇 차단 검증 서비스에 닿지 못해 요청을 통과시킨다 — 동작 {} · {}", checked.get(), unavailable.reason());
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
