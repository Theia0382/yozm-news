package net.yozmnews.platform.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.yozmnews.platform.ProblemResponses;
import net.yozmnews.platform.domain.AdminCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * 보안 구성 — 운영자 경로만 운영자 토큰을 요구하고 나머지는 연다. docs/specs/platform.md §4 · 운영자 접근 통제.
 * <p>
 * 두 역할 공통이다 — 역할에 묶으면 그 역할이 없는 프로세스에 Spring Boot 의 기본 보안(모든 요청 인증 · 생성 비밀번호)이 선다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminSecurityProperties.class)
class SecurityConfiguration {

	/** 운영자 경로 — 정본 */
	static final String ADMIN_PATHS = "/api/admin/**";

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

	@Bean
	@Order(1)
	SecurityFilterChain adminChain(HttpSecurity http, AdminCredential credential,
			AuthenticationProvider adminTokenAuthenticationProvider, ProblemResponses problems) throws Exception {
		http.securityMatcher(ADMIN_PATHS)
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

	@Bean
	@Order(2)
	SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
		return statelessWithoutForms(http).build();
	}

	/**
	 * 세션 · 폼 로그인 · Basic · 로그아웃 · 요청 저장을 쓰지 않는다. CSRF 를 끈다 — 브라우저가 자동으로 붙이는 자격(쿠키 · Basic)이 없다. 쿠키
	 * 세션이 생기면(M4) 켠다.
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
