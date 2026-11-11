package net.yozmnews.storyline.domain.storyline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;

import net.yozmnews.storyline.StorylineState;
import org.junit.jupiter.api.Test;

/**
 * 상태 판정 — 지금 − 마지막 기사 시각이 끝남 기간 이상이면 끝남, 잠잠 기간 이상이면 잠잠, 그 밖은 진행(경계 포함). 두 기간은 0 보다 큰 초
 * 단위이고 잠잠 기간 &lt; 끝남 기간.
 * <p>
 * 지키는 규칙: storyline-R16
 */
class StateCriteriaTests {

	private static final StateCriteria CRITERIA = new StateCriteria(Duration.ofDays(7), Duration.ofDays(30));

	private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

	@Test
	void stateFollowsTheTimeSinceTheLastArticle() {
		// 6일 23시간 59분 59초 — 진행
		assertThat(CRITERIA.stateOf(Instant.parse("2026-11-03T00:00:01Z"), NOW)).isEqualTo(StorylineState.ACTIVE);
		// 정확히 7일 — 잠잠
		assertThat(CRITERIA.stateOf(Instant.parse("2026-11-03T00:00:00Z"), NOW)).isEqualTo(StorylineState.QUIET);
		// 29일 23시간 59분 59초 — 잠잠
		assertThat(CRITERIA.stateOf(Instant.parse("2026-10-11T00:00:01Z"), NOW)).isEqualTo(StorylineState.QUIET);
		// 정확히 30일 — 끝남
		assertThat(CRITERIA.stateOf(Instant.parse("2026-10-11T00:00:00Z"), NOW)).isEqualTo(StorylineState.ENDED);
		// 마지막 기사가 지금보다 뒤 — 진행
		assertThat(CRITERIA.stateOf(Instant.parse("2026-11-11T00:00:00Z"), NOW)).isEqualTo(StorylineState.ACTIVE);
	}

	@Test
	void invalidPeriodsAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new StateCriteria(Duration.ZERO, Duration.ofDays(30)));
		assertThatIllegalArgumentException().isThrownBy(() -> new StateCriteria(Duration.ofDays(-1), Duration.ofDays(30)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new StateCriteria(Duration.ofMillis(500), Duration.ofDays(30)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new StateCriteria(Duration.ofDays(7), Duration.ofDays(30).plusMillis(500)));
		assertThatIllegalArgumentException().isThrownBy(() -> new StateCriteria(Duration.ofDays(7), Duration.ofDays(7)));
		assertThatIllegalArgumentException().isThrownBy(() -> new StateCriteria(Duration.ofDays(8), Duration.ofDays(7)));
	}

}
