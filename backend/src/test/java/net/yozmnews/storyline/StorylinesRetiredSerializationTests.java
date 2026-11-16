package net.yozmnews.storyline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.event.EventId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link StorylinesRetired} 의 직렬화 왕복 — 이벤트 발행 레지스트리가 쓰는 직렬화기로 본다. 남은 사건이 있는 항목과 없는(null) 항목을 함께
 * 싣는다
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class StorylinesRetiredSerializationTests {

	@Autowired
	EventSerializer serializer;

	@Test
	void roundTrips() {
		StorylinesRetired event = new StorylinesRetired(List.of(new RetiredStoryline(new StorylineId(5), null),
				new RetiredStoryline(new StorylineId(2), new EventId(9))));

		assertThat(this.serializer.deserialize(this.serializer.serialize(event), StorylinesRetired.class))
			.isEqualTo(event);
	}

}
