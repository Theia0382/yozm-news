package net.yozmnews.credential;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link CredentialInvalidated} 의 직렬화 왕복 — 이벤트 발행 레지스트리가 쓰는 직렬화기로 본다. 다시 처리되는 발행은 이 왕복을 지난 값이고,
 * 받는 쪽은 키 ID 와 무효 시각(마이크로초)의 짝으로 같은 전이를 알아본다.
 * <p>
 * 지키는 규칙: credential-R15
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class CredentialInvalidatedSerializationTests {

	@Autowired
	EventSerializer serializer;

	@Test
	void roundTripsTheTransitionWithItsMicrosecondTime() {
		CredentialInvalidated event = new CredentialInvalidated(new UserId(7), Provider.ANTHROPIC, 41,
				Instant.parse("2026-11-14T07:30:00.123456Z"));

		CredentialInvalidated read = this.serializer.deserialize(this.serializer.serialize(event),
				CredentialInvalidated.class);

		assertThat(read).isEqualTo(event);
		// 알림의 고유 키가 보는 문자열이 왕복 뒤에도 같다
		assertThat(read.invalidatedAt()).hasToString("2026-11-14T07:30:00.123456Z");
	}

}
