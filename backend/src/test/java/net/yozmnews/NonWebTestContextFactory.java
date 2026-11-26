package net.yozmnews;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

/**
 * 웹이 아닌 시험 컨텍스트의 공장 — 웹이 아닌 종류의 컨텍스트를 {@link GenericApplicationContext} 로 만든다. Spring Boot 가 AOT 실행에서 쓰는
 * 종류와 같다. 웹 종류에는 답하지 않아({@code null}) Spring Boot 의 공장이 그대로 만든다. 시험 클래스패스의 {@code META-INF/spring.factories}
 * 가 등록한다.
 * <p>
 * 까닭: Spring Framework 7.0 의 {@code AnnotationConfigApplicationContext} 는 생성자에서 만든 스캐너가 컴포넌트 스캔에서 읽은 클래스 메타데이터
 * 맵을 refresh 뒤에도 붙잡는다. 시험 컨텍스트 캐시에 남은 웹이 아닌 컨텍스트({@code @JdbcTest} 등)마다 제품 클래스 수에 비례한 메모리를 들고 있게 된다.
 * {@link GenericApplicationContext} 에는 그 스캐너가 없어 refresh 가 끝나면 메타데이터가 풀린다.
 * <p>
 * 지울 조건: 프레임워크의 {@code AnnotationConfigApplicationContext} 가 refresh 뒤 스캐너의 메타데이터를 놓게 되면 이 공장과 등록 줄을 지운다
 * (docs/development/infrastructure.md "테스트 JVM").
 */
public final class NonWebTestContextFactory implements ApplicationContextFactory {

	@Override
	public @Nullable ConfigurableApplicationContext create(@Nullable WebApplicationType webApplicationType) {
		return (webApplicationType == WebApplicationType.NONE) ? new GenericApplicationContext() : null;
	}

}
