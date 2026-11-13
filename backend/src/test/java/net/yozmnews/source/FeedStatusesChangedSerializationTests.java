package net.yozmnews.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link FeedStatusesChanged} 의 직렬화 왕복 — 이벤트 발행 레지스트리가 쓰는 직렬화기로 본다
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class FeedStatusesChangedSerializationTests {

	@Autowired
	EventSerializer serializer;

	@Test
	void roundTripsARunWithItsTransitions() {
		FeedStatusesChanged event = new FeedStatusesChanged(Instant.parse("2026-10-30T00:00:00Z"),
				List.of(new FeedStatusTransition(new FeedId(11), FeedStatus.ACTIVE, FeedStatus.DEGRADED),
						new FeedStatusTransition(new FeedId(12), FeedStatus.STALE, FeedStatus.ACTIVE)));

		assertThat(this.serializer.deserialize(this.serializer.serialize(event), FeedStatusesChanged.class))
			.isEqualTo(event);
	}

}
