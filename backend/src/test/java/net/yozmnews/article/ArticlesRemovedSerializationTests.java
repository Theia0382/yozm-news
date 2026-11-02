package net.yozmnews.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link ArticlesRemoved} 의 직렬화 왕복 — 이벤트 발행 레지스트리가 쓰는 직렬화기로 본다 — 와 생성자의 검사
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ArticlesRemovedSerializationTests {

	@Autowired
	EventSerializer serializer;

	@Test
	void roundTrips() {
		ArticlesRemoved event = new ArticlesRemoved(List.of(new ArticleId(7), new ArticleId(3)));

		assertThat(this.serializer.deserialize(this.serializer.serialize(event), ArticlesRemoved.class))
			.isEqualTo(event);
	}

	@Test
	void rejectsAnEmptyList() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ArticlesRemoved(List.of()));
	}

	@Test
	void rejectsTheSameArticleTwice() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ArticlesRemoved(List.of(new ArticleId(3), new ArticleId(3))));
	}

}
