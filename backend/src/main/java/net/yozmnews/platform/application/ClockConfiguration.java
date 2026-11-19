package net.yozmnews.platform.application;

import java.time.InstantSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 앱 시계의 결선 — docs/specs/platform.md §4
 * <p>
 * main 소스에서 실제 시간의 원천({@link InstantSource#system()})을 만드는 곳은 여기 하나다. bean 메서드의 반환형은 구체 타입이다 — 아직 만들지
 * 않은 bean 의 타입은 반환형으로 예측되므로, 상위 타입으로 선언하면 구체 타입으로 찾는 주입이 bean 생성 순서에 기댄다. 역할 애너테이션이 없다(두
 * 역할 공통).
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

	@Bean
	SystemClock systemClock() {
		return new SystemClock(InstantSource.system());
	}

}
