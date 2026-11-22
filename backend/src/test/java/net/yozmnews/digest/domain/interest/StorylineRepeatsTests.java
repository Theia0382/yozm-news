package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import net.yozmnews.event.EventId;
import net.yozmnews.storyline.StorylineId;
import org.junit.jupiter.api.Test;

/**
 * 다시 본 흐름(반복) 판정 — 기준은 사건의 첫 노출 날짜, 기간은 그 앞 7일(앞선 날짜만).
 * <p>
 * 지키는 규칙: digest-R50
 */
class StorylineRepeatsTests {

	private static final StorylineId TREE = new StorylineId(100);

	private static final StorylineId OTHER = new StorylineId(200);

	private static final LocalDate D = LocalDate.parse("2026-11-20");

	private static final EventId EARLIER = new EventId(1);

	private static final EventId LATER = new EventId(2);

	@Test
	void anEarlierExposureOfTheSameTopExactlySevenDaysBeforeMakesARepeat() {
		assertThat(StorylineRepeats.repeated(Map.of(EARLIER, List.of(TREE), LATER, List.of(TREE)),
				List.of(row(10, D.minusDays(7), EARLIER), row(11, D, LATER))))
			.containsExactly(LATER);
	}

	@Test
	void eightDaysBeforeOrTheSameDateIsNotARepeat() {
		// 8일 전 — 7일 밖
		assertThat(StorylineRepeats.repeated(Map.of(EARLIER, List.of(TREE), LATER, List.of(TREE)),
				List.of(row(10, D.minusDays(8), EARLIER), row(11, D, LATER))))
			.isEmpty();
		// 같은 날짜(같은 날짜를 다시 만든 다이제스트) — 앞선 날짜가 아니다
		assertThat(StorylineRepeats.repeated(Map.of(EARLIER, List.of(TREE), LATER, List.of(TREE)),
				List.of(row(10, D, EARLIER), row(11, D, LATER))))
			.isEmpty();
	}

	@Test
	void anEventWithoutTopsOrWithAnotherTopIsNotARepeat() {
		// 대조 — 꼭대기가 다르면 반복이 아니다
		assertThat(StorylineRepeats.repeated(Map.of(EARLIER, List.of(OTHER), LATER, List.of(TREE)),
				List.of(row(10, D.minusDays(1), EARLIER), row(11, D, LATER))))
			.isEmpty();
		// 꼭대기 없는 사건
		assertThat(StorylineRepeats.repeated(Map.of(EARLIER, List.of(TREE)),
				List.of(row(10, D.minusDays(1), EARLIER), row(11, D, LATER))))
			.isEmpty();
	}

	@Test
	void theFirstExposureOfAnEventDecidesEvenWhenItIsShownAgain() {
		// 대조 — 앞 7일 안에 다른 사건의 같은 꼭대기 노출이 있으면 반복
		assertThat(StorylineRepeats.repeated(Map.of(EARLIER, List.of(TREE), LATER, List.of(TREE)),
				List.of(row(9, D.minusDays(2), EARLIER), row(10, D, LATER), row(11, D.plusDays(3), LATER))))
			.containsExactly(LATER);
		// 사건 2 가 D 에 처음, D+3 에 다시 노출 — 그 앞 7일 안의 같은 꼭대기 노출은 자기 첫 노출뿐이라 반복이 아니다
		assertThat(StorylineRepeats.repeated(Map.of(LATER, List.of(TREE)),
				List.of(row(10, D, LATER), row(11, D.plusDays(3), LATER))))
			.isEmpty();
	}

	@Test
	void anyOfSeveralTopsSeenBeforeMakesARepeat() {
		assertThat(StorylineRepeats.repeated(Map.of(EARLIER, List.of(OTHER), LATER, List.of(TREE, OTHER)),
				List.of(row(10, D.minusDays(3), EARLIER), row(11, D, LATER))))
			.containsExactly(LATER);
	}

	private static ExposureRow row(long digest, LocalDate date, EventId event) {
		return new ExposureRow(digest, date, event, Instant.parse(date + "T00:00:00Z"), 1.0);
	}

}
