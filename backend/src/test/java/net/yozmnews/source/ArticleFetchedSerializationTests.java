package net.yozmnews.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
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
 * 이벤트 발행 레지스트리가 쓰는 직렬화기로 {@link ArticleFetched} 를 저장했다가 되읽을 수 있다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ArticleFetchedSerializationTests {

	@Autowired
	EventSerializer serializer;

	@Test
	void roundTripsEveryValue() {
		ArticleFetched event = new ArticleFetched(new SourceId(7), new FeedId(42), Instant.parse("2026-10-26T00:00:00Z"),
				new FetchedArticle("국회, 예산안 처리", URI.create("https://alpha.example/news/1"), "alpha-1", "여야가 합의했다.",
						Instant.parse("2026-10-25T00:31:38Z"), false, "홍길동", List.of("정치", "국회"),
						URI.create("https://alpha.example/t1.jpg")));

		assertThat(roundTrip(event)).isEqualTo(event);
	}

	@Test
	void roundTripsMissingValues() {
		ArticleFetched event = new ArticleFetched(new SourceId(1), new FeedId(1), Instant.parse("2026-10-26T00:00:00Z"),
				new FetchedArticle("제목과 링크만", URI.create("https://alpha.example/news/t1"), null, null,
						Instant.parse("2026-10-26T00:00:00Z"), true, null, List.of(), null));

		assertThat(roundTrip(event)).isEqualTo(event);
	}

	private ArticleFetched roundTrip(ArticleFetched event) {
		return this.serializer.deserialize(this.serializer.serialize(event), ArticleFetched.class);
	}

}
