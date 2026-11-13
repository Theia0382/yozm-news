package net.yozmnews.user.application;

import static org.mockito.Mockito.mock;

import net.yozmnews.platform.ProblemResponses;
import net.yozmnews.user.domain.session.SessionAccounts;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 웹 계층 시험({@code @WebMvcTest})에 앱의 보안 구성과 문제 응답을 싣는다.
 * <p>
 * 웹 계층 시험은 {@code @Configuration} · {@code @Component} 를 스캔하지 않아, 이것이 없으면 Spring Boot 의 기본 보안(모든 요청 인증)이 서고
 * 오류 조언이 문제 응답 빈을 찾지 못한다. 세션 체인이 읽는 계정 조회는 비어 있는 대역이다 — 웹 계층 시험에는 저장소가 없고, 세션 사용자는 이
 * 계층에서 만들지 않는다(세션 · 계정은 {@code @SpringBootTest} 시험이 본다).
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({ SecurityConfiguration.class, ProblemResponses.class })
public class WebSliceSecurity {

	@Bean
	SessionAccounts sessionAccounts() {
		return mock(SessionAccounts.class);
	}

}
