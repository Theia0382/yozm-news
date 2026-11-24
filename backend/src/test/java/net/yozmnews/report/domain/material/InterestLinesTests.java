package net.yozmnews.report.domain.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.digest.InterestPoint;
import net.yozmnews.digest.TopicSubscriptionStep;
import net.yozmnews.report.domain.input.FlowInterestLine;
import net.yozmnews.report.domain.input.FlowSummary;
import net.yozmnews.report.domain.input.InterestShift;
import net.yozmnews.report.domain.input.TopicInterestLine;
import net.yozmnews.report.domain.input.TopicSubscriptionLine;
import net.yozmnews.report.domain.material.InterestLines.FlowRef;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.TagName;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetNames;
import net.yozmnews.user.TopicSubscriptionAction;
import org.junit.jupiter.api.Test;

/**
 * 관심 줄 — 주제의 체계 순서 · 한쪽만 있는 값 · 흐름의 헤드 · 값도 변화도 없는 흐름 빼기 · 구독 변경의 이름. 지키는 규칙: report-R17
 */
class InterestLinesTests {

	private static final Instant START = Instant.parse("2026-11-16T15:00:00Z");

	private static final Instant END = Instant.parse("2026-11-23T15:00:00Z");

	private static final TagSetNames NAMES = new TagSetNames(new TagSetId(4),
			List.of(new TagName("economy", "경제")),
			List.of(new TagName("economy.trade", "무역"), new TagName("economy.finance", "금융")), List.of());

	private static final FlowSummary SUMMARY = new FlowSummary("금리 인상", "흐름 요약", "최근 전개", START);

	@Test
	void picksTopicFlowAndSubscriptionLines() {
		InterestPoint start = new InterestPoint(START, Map.of("economy.finance", 0.31), Set.of(), Set.of(), List.of(),
				Map.of(new StorylineId(12), 0.4, new StorylineId(20), 0.5), Set.of(), Set.of());
		InterestPoint end = new InterestPoint(END, Map.of("economy.trade", 0.25), Set.of("economy.trade"),
				Set.of("economy.finance"),
				List.of(new TopicSubscriptionStep(Instant.parse("2026-11-18T00:00:00Z"), TopicSubscriptionAction.ADD,
						Set.of("economy.finance", "economy.trade"))),
				Map.of(new StorylineId(12), 0.6), Set.of(new StorylineId(30)), Set.of(new StorylineId(20)));

		InterestLines.Lines lines = InterestLines.of(start, end, List.of(new FlowRef(new StorylineId(30), null),
				new FlowRef(new StorylineId(12), SUMMARY), new FlowRef(new StorylineId(20), null),
				new FlowRef(new StorylineId(40), null), new FlowRef(new StorylineId(12), null)), NAMES);

		// 체계 순서(무역 → 금융), 한쪽 점에만 있는 값은 다른 쪽이 비었다
		assertThat(lines.topics()).containsExactly(
				new TopicInterestLine("economy.trade", "무역", null, 0.25, InterestShift.ENTERED),
				new TopicInterestLine("economy.finance", "금융", 0.31, null, InterestShift.LEFT));
		// 헤드 ID 순, 같은 헤드는 처음 것, 값도 변화도 없는 흐름(40)은 빠진다
		assertThat(lines.flows()).containsExactly(
				new FlowInterestLine(new StorylineId(12), SUMMARY, 0.4, 0.6, InterestShift.NONE),
				new FlowInterestLine(new StorylineId(20), null, 0.5, null, InterestShift.LEFT),
				new FlowInterestLine(new StorylineId(30), null, null, null, InterestShift.ENTERED));
		assertThat(lines.changes()).containsExactly(new TopicSubscriptionLine(Instant.parse("2026-11-18T00:00:00Z"),
				TopicSubscriptionAction.ADD, List.of("무역", "금융")));
	}

	@Test
	void hasNoLinesWithoutSignals() {
		InterestPoint quiet = new InterestPoint(END, Map.of(), Set.of(), Set.of(), List.of(), Map.of(), Set.of(),
				Set.of());

		InterestLines.Lines lines = InterestLines.of(quiet, quiet, List.of(new FlowRef(new StorylineId(12), SUMMARY)),
				NAMES);

		assertThat(lines.topics()).isEmpty();
		assertThat(lines.flows()).isEmpty();
		assertThat(lines.changes()).isEmpty();
	}

}
