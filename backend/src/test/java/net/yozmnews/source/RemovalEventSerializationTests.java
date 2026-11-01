package net.yozmnews.source;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link SourceDeleted} · {@link FeedDeleted} 의 직렬화 왕복 — 이벤트 발행 레지스트리가 쓰는 직렬화기로 본다
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class RemovalEventSerializationTests {

	@Autowired
	EventSerializer serializer;

	@Test
	void roundTripsASourceDeletion() {
		SourceDeleted event = new SourceDeleted(new SourceId(7));

		assertThat(this.serializer.deserialize(this.serializer.serialize(event), SourceDeleted.class)).isEqualTo(event);
	}

	@Test
	void roundTripsAFeedDeletion() {
		FeedDeleted event = new FeedDeleted(new SourceId(7), new FeedId(42));

		assertThat(this.serializer.deserialize(this.serializer.serialize(event), FeedDeleted.class)).isEqualTo(event);
	}

}
