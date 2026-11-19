package net.yozmnews.platform.application;

import java.time.InstantSource;

import net.yozmnews.platform.domain.ActiveRoles;
import net.yozmnews.platform.domain.ClockStart;
import net.yozmnews.platform.domain.MeasurementClockRows;
import net.yozmnews.platform.domain.MeasurementClockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 앱 시계의 결선 — docs/specs/platform.md §4
 * <p>
 * main 소스에서 실제 시간의 원천({@link InstantSource#system()})을 만드는 곳은 여기 하나다. 측정 profile 이면 측정용 시계, 아니면 실제 시간의
 * 시계를 등록한다 — 둘 가운데 언제나 하나다. 기동 판정은 {@link ClockStart} 가 한다. 시계 bean 은 DB 를 읽으므로 DB 초기화(Flyway) 뒤에
 * 만들어진다(읽기가 받는 {@code JdbcClient} 가 그 뒤에 만들어진다). bean 메서드의 반환형은 구체 타입이다 — 아직 만들지 않은 bean 의 타입은
 * 반환형으로 예측되므로, 상위 타입으로 선언하면 구체 타입으로 찾는 주입이 bean 생성 순서에 기댄다. 역할 애너테이션이 없다(두 역할 공통).
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(ClockConfiguration.class);

	@Bean
	@Profile("!" + MeasurementOnly.PROFILE)
	SystemClock systemClock(MeasurementClockRows rows) {
		ClockStart.requireNoMeasurementRow(rows.exists(), MeasurementOnly.PROFILE);
		return new SystemClock(InstantSource.system());
	}

	@Bean
	@MeasurementOnly
	MeasurementClock measurementClock(ActiveRoles roles, MeasurementClockRows rows) {
		InstantSource real = InstantSource.system();
		MeasurementClockState state = ClockStart.measurement(roles, rows.load(), MeasurementOnly.PROFILE);
		if (state.anchoredAt() == null) {
			rows.anchor(real.instant());
			state = ClockStart.measurement(roles, rows.load(), MeasurementOnly.PROFILE);
		}
		MeasurementClock clock = new MeasurementClock(state, real, rows);
		logger.warn("측정용 시계로 뜬다 — 지금 {}, 출발 {}, 넘긴 기록 {}건. 수집하지 않는다", clock.instant(), state.startedAt(),
				state.jumps().jumps().size());
		return clock;
	}

}
