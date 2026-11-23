package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.DigestCompleted;
import org.junit.jupiter.api.Test;

/**
 * 다이제스트 끝남 리스너 — 받은 이벤트의 사용자 · 다이제스트로 정리 작업을 등록한다. 이벤트의 다이제스트 ID 는 1 이상이다. 스프링 없이 돈다.
 */
class DigestCompletedListenerTests {

	@Test
	void enqueuesTheSummaryJobOfThatUserAndDigest() {
		List<String> enqueued = new ArrayList<>();
		DigestCompletedListener listener = new DigestCompletedListener(
				(user, digestId) -> enqueued.add(user.value() + ":" + digestId));

		listener.on(new DigestCompleted(new UserId(7), 77, LocalDate.parse("2026-11-23"),
				Instant.parse("2026-11-22T22:00:00Z")));

		assertThat(enqueued).containsExactly("7:77");
	}

	@Test
	void theDigestIdentifierStartsAtOne() {
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestCompleted(new UserId(7), 0,
				LocalDate.parse("2026-11-23"), Instant.parse("2026-11-22T22:00:00Z")));
	}

}
