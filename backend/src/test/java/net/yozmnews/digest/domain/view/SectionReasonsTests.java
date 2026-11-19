package net.yozmnews.digest.domain.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.digest.SectionKind;
import net.yozmnews.digest.SelectionReason;
import org.junit.jupiter.api.Test;

/**
 * 항목(섹션)의 선정 이유 하나 — 구독 주제가 아닌 이유를 고른다: 큰 화제 &gt; 관심 반영 &gt; 구독 주제.
 * <p>
 * 지키는 규칙: digest-R48
 */
class SectionReasonsTests {

	@Test
	void aBigSlotEventMakesTheItemABigTopicWhateverElseItHolds() {
		assertThat(SectionReasons.of(SectionKind.TOP_STORY, List.of(SelectionReason.SUBSCRIBED,
				SelectionReason.INTEREST, SelectionReason.MAJOR_STORYLINE))).isEqualTo(SelectionReason.MAJOR_STORYLINE);
	}

	@Test
	void anInterestEventMarksAnItemWithoutABigSlotEvent() {
		assertThat(SectionReasons.of(SectionKind.HEADLINE, List.of(SelectionReason.SUBSCRIBED, SelectionReason.INTEREST)))
			.isEqualTo(SelectionReason.INTEREST);
		// 대조 — 구독 사건뿐이면 구독 주제(화면에 배지 없음)
		assertThat(SectionReasons.of(SectionKind.HEADLINE, List.of(SelectionReason.SUBSCRIBED, SelectionReason.SUBSCRIBED)))
			.isEqualTo(SelectionReason.SUBSCRIBED);
	}

	@Test
	void theOverviewHasNoReasonAndACitingSectionWithoutEventsIsSubscribed() {
		assertThat(SectionReasons.of(SectionKind.OVERVIEW, List.of(SelectionReason.MAJOR_STORYLINE))).isNull();
		assertThat(SectionReasons.of(SectionKind.TOP_STORY, List.of())).isEqualTo(SelectionReason.SUBSCRIBED);
	}

}
