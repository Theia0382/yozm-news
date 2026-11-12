package net.yozmnews.user.adapter.in.web;

import java.util.List;

import net.yozmnews.platform.WebRole;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 화면용 API 의 세션 사용자 인자 — docs/specs/user.md §4 · user-R6
 */
@WebRole
@Configuration(proxyBeanMethods = false)
class UserWebConfiguration implements WebMvcConfigurer {

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new SessionUserArgumentResolver());
	}

}
