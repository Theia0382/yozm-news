package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.InstantSource;
import java.time.OffsetDateTime;

import net.yozmnews.MeasurementClockRow;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.application.MeasurementClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 측정 profile 로 뜬 프로세스 — 시계 행이 있으면 앱의 시계가 측정용 시계이고, 처음 뜰 때 출발에 대응하는 실제 시각을 채운다. 시계 bean 은 여전히
 * 하나이고 {@code java.time.Clock} 이 아니다. MeasurementScheduleDaysTests 와 같은 구성이라 컨텍스트를 함께 쓴다.
 */
@Import({ TestcontainersConfiguration.class, MeasurementClockRow.class })
@SpringBootTest
@ActiveProfiles({ "web", "worker", "measurement" })
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class MeasurementClockStartupTests {

	@Autowired
	ApplicationContext context;

	@Autowired
	JdbcClient jdbc;

	@Test
	void theAppClockIsTheMeasurementClockAnchoredOnTheFirstStart() {
		assertThat(this.context.getBeansOfType(InstantSource.class)).hasSize(1);
		assertThat(this.context.getBean(AppClock.class)).isInstanceOf(MeasurementClock.class);
		assertThat(this.context.getBeansOfType(Clock.class)).isEmpty();
		assertThat(this.context.getBean(AppClock.class).realTime()).isFalse();
		assertThat(this.context.getBean(MeasurementClockAdministration.class).status())
			.isInstanceOf(MeasurementClockStatus.On.class);

		OffsetDateTime anchored = this.jdbc.sql("select anchored_at from measurement_clock where id = 1")
			.query(OffsetDateTime.class)
			.single();
		assertThat(anchored).isNotNull();
		assertThat(this.jdbc.sql("select started_at from measurement_clock where id = 1")
			.query(OffsetDateTime.class)
			.single()
			.toInstant()).isEqualTo(MeasurementClockRow.STARTED);
	}

}
