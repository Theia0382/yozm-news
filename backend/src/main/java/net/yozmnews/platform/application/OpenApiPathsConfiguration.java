package net.yozmnews.platform.application;

import java.util.List;

import net.yozmnews.platform.ApiPaths;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 화면용 API 명세가 담는 경로 — 화면용 API 경로({@link ApiPaths#API})만. docs/specs/platform.md §4 · platform-R6 · platform-R21
 * <p>
 * 두 역할 공통이다 — 작업 역할만 켠 프로세스의 명세도 같은 경로만 담는다.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiPathsConfiguration {

	/** springdoc 설정이 만들어질 때 담는 경로를 정한다 — 설정 파일에 두 번째 사본을 두지 않는다 */
	@Bean
	static BeanPostProcessor screenApiPathsOnly() {
		return new BeanPostProcessor() {

			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) {
				if (bean instanceof SpringDocConfigProperties properties) {
					properties.setPathsToMatch(List.of(ApiPaths.API));
				}
				return bean;
			}

		};
	}

}
