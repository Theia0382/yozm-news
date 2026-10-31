package net.yozmnews.platform.application;

import net.yozmnews.platform.ProblemResponses;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 웹 계층 시험({@code @WebMvcTest})에 앱의 보안 구성과 문제 응답을 싣는다.
 * <p>
 * 웹 계층 시험은 {@code @Configuration} · {@code @Component} 를 스캔하지 않아, 이것이 없으면 Spring Boot 의 기본 보안(모든 요청 인증)이 서고
 * 오류 조언이 문제 응답 빈을 찾지 못한다.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({ SecurityConfiguration.class, ProblemResponses.class })
public class WebSliceSecurity {

}
