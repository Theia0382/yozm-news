package net.yozmnews.user.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.yozmnews.platform.ApiPaths;
import net.yozmnews.platform.ProblemResponses;
import net.yozmnews.user.domain.AdminCredential;
import net.yozmnews.user.domain.account.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 보안 구성 — 체인 셋: 운영자 경로는 운영자 토큰, 그 밖의 화면용 API 는 세션 · CSRF, 나머지는 연다. docs/specs/user.md §4 · user-R1 ·
 * user-R2 · user-R4 · user-R5 · user-R7 · user-R10
 * <p>
 * 두 역할 공통이다 — 역할에 묶으면 그 역할이 없는 프로세스에 Spring Boot 의 기본 보안(모든 요청 인증 · 생성 비밀번호)이 선다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminSecurityProperties.class)
class SecurityConfiguration {

	/** 운영자 역할 이름 */
	static final String ADMIN_ROLE = "ADMIN";

	private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

	/**
	 * 운영자 자격 — 설정 값의 형식이 틀리면 기동하지 않는다.
	 * @throws IllegalArgumentException 형식이 틀렸을 때 — 메시지에 값을 담지 않는다
	 */
	@Bean
	AdminCredential adminCredential(AdminSecurityProperties properties) {
		AdminCredential credential = AdminCredential.of(properties.token());
		if (credential.open()) {
			logger.info("운영자 기능을 연다");
		}
		else {
			logger.info("운영자 토큰(yozmnews.admin.token)이 없어 운영자 기능을 닫는다");
		}
		return credential;
	}

	/** 운영자 토큰 대조 — 이 빈이 있어 Spring Boot 가 기본 사용자 · 생성 비밀번호를 만들지 않는다 */
	@Bean
	AuthenticationProvider adminTokenAuthenticationProvider(AdminCredential credential) {
		return new AdminTokenAuthenticationProvider(credential);
	}

	/** 세션의 보안 문맥 저장소 — 세션 체인과 로그인이 함께 쓴다. 읽지 못하는 세션은 지운다(user-R7) */
	@Bean
	SessionSecurityContexts sessionSecurityContexts() {
		return new SessionSecurityContexts();
	}

	/** CSRF 토큰 저장소 — 세션 체인과 로그인 · 로그아웃이 함께 쓴다 */
	@Bean
	CookieCsrfTokenRepository csrfTokenRepository() {
		return SessionCookies.csrfTokens();
	}

	@Bean
	SessionSignIn sessionSignIn(SessionSecurityContexts sessionSecurityContexts,
			CookieCsrfTokenRepository csrfTokenRepository) {
		return new SessionSignIn(sessionSecurityContexts, csrfTokenRepository);
	}

	@Bean
	@Order(1)
	SecurityFilterChain adminChain(HttpSecurity http, AdminCredential credential,
			AuthenticationProvider adminTokenAuthenticationProvider, ProblemResponses problems) throws Exception {
		http.securityMatcher(ApiPaths.ADMIN)
			.authorizeHttpRequests(requests -> requests.anyRequest().hasRole(ADMIN_ROLE))
			.addFilterBefore(new AdminTokenFilter(adminTokenAuthenticationProvider), AuthorizationFilter.class)
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(
						(request, response, ex) -> unauthenticated(request, response, credential, problems))
				.accessDeniedHandler((request, response, ex) -> problems.write(response,
						problems.problem(ProblemTypes.ADMIN_FORBIDDEN, ProblemTypes.ADMIN_FORBIDDEN_TITLE,
								HttpStatus.FORBIDDEN, "운영자 권한이 없다", request))));
		return statelessWithoutForms(http).build();
	}

	/**
	 * 세션 체인 — 운영자 경로 밖의 화면용 API. 세션의 사용자 ID 로 요청마다 계정을 읽고(user-R7), 공개 표시가 없는 처리기는 세션 사용자를
	 * 요구하며(user-R5), 변경 요청은 CSRF 토큰을 요구한다(user-R10).
	 */
	@Bean
	@Order(2)
	SecurityFilterChain sessionChain(HttpSecurity http, AccountRepository accounts,
			@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
			SessionSecurityContexts sessionSecurityContexts,
			CookieCsrfTokenRepository csrfTokenRepository, ProblemResponses problems) throws Exception {
		http.securityMatcher(ApiPaths.API)
			.securityContext(context -> context.securityContextRepository(sessionSecurityContexts))
			.sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
			.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
			.addFilterAfter(new SessionAccountFilter(accounts), SecurityContextHolderFilter.class)
			.authorizeHttpRequests(
					requests -> requests.anyRequest().access(new PublicApiAuthorization(handlerMapping)))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, ex) -> problems.write(response,
						problems.problem(ProblemTypes.UNAUTHENTICATED, ProblemTypes.UNAUTHENTICATED_TITLE,
								HttpStatus.UNAUTHORIZED, "로그인하지 않았거나 세션이 만료됐다", request)))
				.accessDeniedHandler((request, response, ex) -> problems.write(response,
						(ex instanceof CsrfException)
								? problems.problem(ProblemTypes.CSRF_REJECTED, ProblemTypes.CSRF_REJECTED_TITLE,
										HttpStatus.FORBIDDEN, "요청 보호 토큰(X-XSRF-TOKEN)이 없거나 맞지 않는다", request)
								: problems.problem(ProblemResponses.BLANK,
										ProblemResponses.standardTitle(HttpStatus.FORBIDDEN), HttpStatus.FORBIDDEN,
										"이 요청을 할 수 없다", request))))
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable);
		return http.build();
	}

	@Bean
	@Order(3)
	SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
		return statelessWithoutForms(http).build();
	}

	/**
	 * 운영자 · 기본 체인 — 세션 · 폼 로그인 · Basic · 로그아웃 · 요청 저장을 쓰지 않는다. 세션 쿠키를 읽지 않아 브라우저가 자동으로 붙이는 자격이
	 * 없으므로 CSRF 를 끈다.
	 */
	private static HttpSecurity statelessWithoutForms(HttpSecurity http) throws Exception {
		return http.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
	}

	/** 인증되지 않은 운영자 요청 — 닫혔으면 403, 열렸으면 401 과 Bearer 요구 */
	private static void unauthenticated(HttpServletRequest request, HttpServletResponse response,
			AdminCredential credential, ProblemResponses problems) throws java.io.IOException {
		if (!credential.open()) {
			problems.write(response,
					problems.problem(ProblemTypes.ADMIN_CLOSED, ProblemTypes.ADMIN_CLOSED_TITLE, HttpStatus.FORBIDDEN,
							"이 인스턴스는 운영자 토큰(YOZMNEWS_ADMIN_TOKEN)을 설정하지 않아 운영자 기능을 열지 않았다", request));
			return;
		}
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		problems.write(response, problems.problem(ProblemTypes.ADMIN_UNAUTHORIZED,
				ProblemTypes.ADMIN_UNAUTHORIZED_TITLE, HttpStatus.UNAUTHORIZED, "운영자 토큰이 없거나 맞지 않는다", request));
	}

}
