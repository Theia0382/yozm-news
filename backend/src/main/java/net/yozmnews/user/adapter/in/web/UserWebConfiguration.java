package net.yozmnews.user.adapter.in.web;

import java.util.List;

import net.yozmnews.platform.UserId;
import net.yozmnews.platform.WebRole;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 화면용 API 의 세션 사용자 인자 — docs/specs/user.md §4 · user-R6.
 * <p>
 * 세션 사용자 인자는 요청에서 받지 않으므로 화면용 API 명세에 그리지 않는다 — 그리지 않으면 명세가 묶기 애너테이션 없는 인자를 질의 인자로
 * 그려, 화면의 생성 타입이 사용자 ID 를 보내라고 요구하게 된다.
 */
@WebRole
@Configuration(proxyBeanMethods = false)
class UserWebConfiguration implements WebMvcConfigurer {

	static {
		SpringDocUtils.getConfig().addRequestWrapperToIgnore(UserId.class);
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new SessionUserArgumentResolver());
	}

}
