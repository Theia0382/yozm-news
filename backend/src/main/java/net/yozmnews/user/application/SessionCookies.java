package net.yozmnews.user.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * 세션 · CSRF 쿠키 속성의 정본 — 언제나 {@code Secure} 이고 {@code SameSite=Lax} 다. docs/specs/user.md §4 · user-R8 · user-R9
 * <p>
 * Spring Boot 의 쿠키 설정 키에 기대지 않고 여기 한 곳에 둔다 — 이 빈이 있으면 Spring Boot 가 쿠키 직렬화기를 만들지 않는다.
 */
@Configuration(proxyBeanMethods = false)
class SessionCookies {

	/** 세션 쿠키 이름 */
	static final String SESSION_COOKIE = "SESSION";

	/** CSRF 토큰 쿠키 이름 */
	static final String CSRF_COOKIE = "XSRF-TOKEN";

	/** 쿠키의 SameSite 속성 */
	private static final String SAME_SITE = "Lax";

	/**
	 * 세션 쿠키 — 로그인 유지 요청 속성이 있으면 지속 쿠키로 써서 수명을 서버 만료에 맡기고, 없으면 브라우저 세션 쿠키다(user-R8).
	 */
	@Bean
	CookieSerializer sessionCookieSerializer() {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(SESSION_COOKIE);
		serializer.setCookiePath("/");
		serializer.setUseHttpOnlyCookie(true);
		serializer.setUseSecureCookie(true);
		serializer.setSameSite(SAME_SITE);
		serializer.setRememberMeRequestAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR);
		return serializer;
	}

	/** CSRF 토큰 쿠키 — 화면이 읽어 헤더로 돌려보내므로 {@code HttpOnly} 가 아니다(user-R9 · user-R10) */
	static CookieCsrfTokenRepository csrfTokens() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieName(CSRF_COOKIE);
		repository.setCookieCustomizer(cookie -> cookie.path("/").secure(true).sameSite(SAME_SITE));
		return repository;
	}

}
