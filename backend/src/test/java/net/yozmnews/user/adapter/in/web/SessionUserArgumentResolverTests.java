package net.yozmnews.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 세션 사용자 인자 — 묶기 애너테이션이 없는 {@link UserId} 만 채운다. 세션 사용자로 채우는 동작은 {@code SessionChainTests} 가 실제 세션으로
 * 본다.
 * <p>
 * 지키는 규칙: user-R6
 */
class SessionUserArgumentResolverTests {

	private final SessionUserArgumentResolver resolver = new SessionUserArgumentResolver();

	@Test
	void supportsOnlyUnannotatedUserIds() throws Exception {
		Method handler = Handlers.class.getDeclaredMethod("handle", UserId.class, UserId.class, UserId.class,
				long.class);

		assertThat(this.resolver.supportsParameter(new MethodParameter(handler, 0))).isTrue();
		assertThat(this.resolver.supportsParameter(new MethodParameter(handler, 1))).as("경로에서 받는 UserId").isFalse();
		assertThat(this.resolver.supportsParameter(new MethodParameter(handler, 2))).as("질의에서 받는 UserId").isFalse();
		assertThat(this.resolver.supportsParameter(new MethodParameter(handler, 3))).as("UserId 가 아닌 인자").isFalse();
	}

	static class Handlers {

		void handle(UserId session, @PathVariable UserId path, @RequestParam UserId query, long other) {
		}

	}

}
