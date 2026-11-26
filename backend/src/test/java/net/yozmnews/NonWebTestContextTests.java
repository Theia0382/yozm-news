package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 웹이 아닌 시험 컨텍스트의 종류 — 시험 클래스패스에 등록한 {@link NonWebTestContextFactory} 가 웹이 아닌 시험 컨텍스트를
 * {@link GenericApplicationContext} 로 만드는지 본다(docs/development/infrastructure.md "테스트 JVM"). 클래스 머리의 구성은
 * {@link AccountJobMoveMigrationTests} 와 같아 컨텍스트 캐시의 같은 컨텍스트를 쓴다.
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class NonWebTestContextTests {

	@Autowired
	ApplicationContext context;

	@Test
	void sliceContextsAreGenericApplicationContexts() {
		// 대조 — 슬라이스 컨텍스트를 실제로 받았다(등록한 공장이 없을 때의 종류 AnnotationConfigApplicationContext 도 이 계열이다)
		assertThat(this.context).isInstanceOf(GenericApplicationContext.class);
		assertThat(this.context.getClass()).isEqualTo(GenericApplicationContext.class);
	}

	@Test
	void theRegisteredFactoryMakesOnlyNonWebContexts() {
		// 대조 — 웹 종류는 Spring Boot 의 공장이 그대로 만들고, 등록한 공장은 웹 종류에 답하지 않는다
		try (ConfigurableApplicationContext servlet = ApplicationContextFactory.DEFAULT
			.create(WebApplicationType.SERVLET)) {
			assertThat(servlet).isExactlyInstanceOf(AnnotationConfigServletWebServerApplicationContext.class);
		}
		assertThat(new NonWebTestContextFactory().create(WebApplicationType.SERVLET)).isNull();
		assertThat(new NonWebTestContextFactory().create(WebApplicationType.REACTIVE)).isNull();

		try (ConfigurableApplicationContext nonWeb = ApplicationContextFactory.DEFAULT.create(WebApplicationType.NONE)) {
			assertThat(nonWeb).isExactlyInstanceOf(GenericApplicationContext.class);
		}
	}

}
