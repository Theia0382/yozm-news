package net.yozmnews.user.application;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.yozmnews.credential.LlmKeys;
import net.yozmnews.platform.ApiPaths;
import net.yozmnews.platform.ApiProblem;
import net.yozmnews.platform.ProblemResponses;
import net.yozmnews.user.domain.AdminCredential;
import net.yozmnews.user.domain.session.AdminAccess;
import net.yozmnews.user.domain.session.SessionAccess;
import net.yozmnews.user.domain.session.SessionAccounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 보안 구성 — 체인 셋: 운영자 경로는 운영자 계정 세션 · CSRF 와 스크립트용 운영자 토큰, 그 밖의 화면용 API 는 세션 · CSRF, 나머지는 연다.
 * docs/specs/user.md §4 · user-R1 · user-R2 · user-R4 · user-R5 · user-R7 · user-R10
 * <p>
 * 두 역할 공통이다 — 역할에 묶으면 그 역할이 없는 프로세스에 Spring Boot 의 기본 보안(모든 요청 인증 · 생성 비밀번호)이 선다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminSecurityProperties.class)
class SecurityConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

	/**
	 * 운영자 자격 — 설정 값의 형식이 틀리면 기동하지 않는다.
	 * @throws IllegalArgumentException 형식이 틀렸을 때 — 메시지에 값을 담지 않는다
	 */
	@Bean
	AdminCredential adminCredential(AdminSecurityProperties properties) {
		AdminCredential credential = AdminCredential.of(properties.token());
		if (credential.configured()) {
			logger.info("운영자 토큰(Bearer)을 받는다");
		}
		else {
			logger.info("운영자 토큰(yozmnews.admin.token)이 없어 Bearer 인증을 받지 않는다 — 운영자 경로는 ADMIN 계정 세션으로 연다");
		}
		return credential;
	}

	/** 운영자 토큰 대조 — 이 빈이 있어 Spring Boot 가 기본 사용자 · 생성 비밀번호를 만들지 않는다 */
	@Bean
	AuthenticationProvider adminTokenAuthenticationProvider(AdminCredential credential) {
		return new AdminTokenAuthenticationProvider(credential);
	}

	/** 세션의 보안 문맥 저장소 — 두 세션 인증 체인과 로그인이 함께 쓴다. 읽지 못하는 세션은 지운다(user-R7) */
	@Bean
	SessionSecurityContexts sessionSecurityContexts() {
		return new SessionSecurityContexts();
	}

	/** CSRF 토큰 저장소 — 두 세션 인증 체인과 로그인 · 로그아웃이 함께 쓴다 */
	@Bean
	CookieCsrfTokenRepository csrfTokenRepository() {
		return SessionCookies.csrfTokens();
	}

	@Bean
	SessionSignIn sessionSignIn(SessionSecurityContexts sessionSecurityContexts,
			CookieCsrfTokenRepository csrfTokenRepository) {
		return new SessionSignIn(sessionSecurityContexts, csrfTokenRepository);
	}

	/**
	 * 운영자 체인 — 운영자 경로. 세션 사용자는 세션 체인과 같은 구성으로 읽고, {@code Authorization} 헤더를 낸 요청은 운영자 토큰으로만
	 * 판정한다(틀리면 401 {@code admin-unauthorized} 로 끝). 변경 요청은 CSRF 토큰을 요구하되 운영자 토큰으로 인증한 요청은 면한다 — 쿠키
	 * 자격이 아니다. 인가는 {@link AdminAccess}(토큰 · 세션 사용자 · 강제 변경 · 역할 — LLM 키는 보지 않는다)다(user-R1).
	 */
	@Bean
	@Order(1)
	SecurityFilterChain adminChain(HttpSecurity http, SessionAccounts accounts,
			SessionSecurityContexts sessionSecurityContexts, CookieCsrfTokenRepository csrfTokenRepository,
			AuthenticationProvider adminTokenAuthenticationProvider, ProblemResponses problems) throws Exception {
		http.securityMatcher(ApiPaths.ADMIN);
		sessionAuthentication(http, accounts, sessionSecurityContexts)
			.addFilterBefore(new AdminTokenFilter(adminTokenAuthenticationProvider, problems), CsrfFilter.class)
			.csrf(csrf -> csrf.spa()
				.csrfTokenRepository(csrfTokenRepository)
				.ignoringRequestMatchers(request -> AdminTokenAuthentication
					.isOperator(SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication())))
			.authorizeHttpRequests(requests -> requests.anyRequest().access(new AdminAuthorization()))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, ex) -> writeUnauthenticated(request, response, problems))
				.accessDeniedHandler(
						(request, response, ex) -> problems.write(response, adminDenial(request, ex, problems))));
		return http.build();
	}

	/**
	 * 세션 체인 — 운영자 경로 밖의 화면용 API. 세션의 사용자 ID 로 요청마다 계정과 비밀번호 강제 변경 여부를 읽고(user-R7), 공개 표시가
	 * 없는 처리기는 세션 사용자를 요구하되 비밀번호를 바꿔야 하는 세션 사용자에게는 비밀번호 변경 전 허용 표시가 있는 처리기만, LLM 키가 없는
	 * 세션 사용자에게는 키 없이 허용 표시가 있는 처리기만 열며(user-R5), 변경 요청은 CSRF 토큰을 요구한다(user-R10). 인가 뒤에는 요청 제한
	 * 표시가 있는 처리기의 요청을 처리기 앞에서 세고, 봇 차단 표시가 있으면 사람 확인 토큰을 본다(user-R40 · user-R41).
	 */
	@Bean
	@Order(2)
	SecurityFilterChain sessionChain(HttpSecurity http, SessionAccounts accounts,
			@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
			SessionSecurityContexts sessionSecurityContexts, CookieCsrfTokenRepository csrfTokenRepository,
			LlmKeys llmKeys, RequestLimiter requestLimiter, HumanCheck humanCheck, ProblemResponses problems)
			throws Exception {
		HandlerLookup handlers = new HandlerLookup(handlerMapping);
		http.securityMatcher(ApiPaths.API);
		sessionAuthentication(http, accounts, sessionSecurityContexts)
			.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
			.addFilterAfter(new RequestGuardFilter(handlers, requestLimiter, humanCheck, problems),
					AuthorizationFilter.class)
			.authorizeHttpRequests(
					requests -> requests.anyRequest().access(new PublicApiAuthorization(handlers, llmKeys)))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, ex) -> writeUnauthenticated(request, response, problems))
				.accessDeniedHandler(
						(request, response, ex) -> problems.write(response, sessionDenial(request, ex, problems))));
		return http.build();
	}

	@Bean
	@Order(3)
	SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		return http.build();
	}

	/**
	 * 세션 인증 공통 구성 — 세션 문맥 저장소 · 세션 생성 정책 · 계정 읽기 필터, 폼 로그인 · Basic · 로그아웃 · 요청 저장 끔. 운영자 체인과
	 * 세션 체인이 함께 쓴다. CSRF 는 체인마다 면제 조건이 달라 각 체인이 건다(저장소는 같은 빈).
	 */
	private static HttpSecurity sessionAuthentication(HttpSecurity http, SessionAccounts accounts,
			SessionSecurityContexts sessionSecurityContexts) throws Exception {
		return http.securityContext(context -> context.securityContextRepository(sessionSecurityContexts))
			.sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
			.addFilterAfter(new SessionAccountFilter(accounts), SecurityContextHolderFilter.class)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable);
	}

	/** 두 세션 인증 체인의 401 — 세션 사용자가 없다(user-R3) */
	private static void writeUnauthenticated(HttpServletRequest request, HttpServletResponse response,
			ProblemResponses problems) throws IOException {
		problems.write(response, problems.problem(ProblemTypes.UNAUTHENTICATED, ProblemTypes.UNAUTHENTICATED_TITLE,
				HttpStatus.UNAUTHORIZED, "로그인하지 않았거나 세션이 만료됐다", request));
	}

	/** 두 세션 인증 체인의 403 — CSRF 토큰이 없거나 틀리다(user-R10) */
	private static ApiProblem csrfRejected(HttpServletRequest request, ProblemResponses problems) {
		return problems.problem(ProblemTypes.CSRF_REJECTED, ProblemTypes.CSRF_REJECTED_TITLE, HttpStatus.FORBIDDEN,
				"요청 보호 토큰(X-XSRF-TOKEN)이 없거나 맞지 않는다", request);
	}

	/** 두 세션 인증 체인의 403 — 세션 사용자가 비밀번호를 바꿔야 한다(user-R29) */
	private static ApiProblem passwordChangeRequired(HttpServletRequest request, ProblemResponses problems) {
		return problems.problem(ProblemTypes.PASSWORD_CHANGE_REQUIRED, ProblemTypes.PASSWORD_CHANGE_REQUIRED_TITLE,
				HttpStatus.FORBIDDEN, "비밀번호를 바꾸기 전에는 비밀번호 변경 · 로그인 상태 조회 · 로그아웃만 쓴다", request);
	}

	/** 판정 값이 없는 거부 — 일반 403 */
	private static ApiProblem forbidden(HttpServletRequest request, ProblemResponses problems) {
		return problems.problem(ProblemResponses.BLANK, ProblemResponses.standardTitle(HttpStatus.FORBIDDEN),
				HttpStatus.FORBIDDEN, "이 요청을 할 수 없다", request);
	}

	/**
	 * 운영자 체인의 403 — CSRF 토큰이 없거나 틀리면 {@code csrf-rejected}, 인가가 남긴 판정 값이 비밀번호 변경 필요면
	 * {@code password-change-required}, 운영자 아님이면 {@code admin-forbidden}, 판정 값이 없는 그 밖의 거부는 일반 403(user-R1 ·
	 * user-R3)
	 */
	private static ApiProblem adminDenial(HttpServletRequest request, AccessDeniedException ex,
			ProblemResponses problems) {
		if (ex instanceof CsrfException) {
			return csrfRejected(request, problems);
		}
		if (!(request.getAttribute(AdminAuthorization.DENIAL) instanceof AdminAccess denial)) {
			return forbidden(request, problems);
		}
		return switch (denial) {
			case PASSWORD_CHANGE_REQUIRED -> passwordChangeRequired(request, problems);
			case FORBIDDEN -> problems.problem(ProblemTypes.ADMIN_FORBIDDEN, ProblemTypes.ADMIN_FORBIDDEN_TITLE,
					HttpStatus.FORBIDDEN, "운영자 권한이 없다", request);
			// 여는 판정은 거부 처리기에 오지 않고, 인증이 없는 거부는 인증 진입점이 401 로 낸다
			case OPEN, UNAUTHENTICATED ->
				throw new IllegalStateException("운영자 체인의 거부 처리기에 올 수 없는 판정이다: " + denial);
		};
	}

	/**
	 * 세션 체인의 403 — CSRF 토큰이 없거나 틀리면 {@code csrf-rejected}, 인가가 남긴 판정 값이 비밀번호 변경 필요면
	 * {@code password-change-required}, 키 필요면 {@code llm-key-required}, 판정 값이 없는 그 밖의 거부는 일반 403(user-R3 · user-R5 ·
	 * user-R10)
	 */
	private static ApiProblem sessionDenial(HttpServletRequest request, AccessDeniedException ex,
			ProblemResponses problems) {
		if (ex instanceof CsrfException) {
			return csrfRejected(request, problems);
		}
		if (!(request.getAttribute(PublicApiAuthorization.DENIAL) instanceof SessionAccess denial)) {
			return forbidden(request, problems);
		}
		return switch (denial) {
			case PASSWORD_CHANGE_REQUIRED -> passwordChangeRequired(request, problems);
			case LLM_KEY_REQUIRED -> problems.problem(ProblemTypes.LLM_KEY_REQUIRED,
					ProblemTypes.LLM_KEY_REQUIRED_TITLE, HttpStatus.FORBIDDEN,
					"LLM 키를 등록하기 전에는 키 등록 · 계정 · 알림 · 관심 설정만 쓴다", request);
			// 여는 판정은 거부 처리기에 오지 않고, 세션 사용자가 없는 거부는 인증 진입점이 401 로 낸다
			case OPEN, UNAUTHENTICATED ->
				throw new IllegalStateException("세션 체인의 거부 처리기에 올 수 없는 판정이다: " + denial);
		};
	}

}
