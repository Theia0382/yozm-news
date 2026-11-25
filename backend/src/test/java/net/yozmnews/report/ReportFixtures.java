package net.yozmnews.report;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import net.yozmnews.event.EventId;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.InterestShift;
import net.yozmnews.report.domain.input.Count;
import net.yozmnews.report.domain.input.EventLine;
import net.yozmnews.report.domain.input.InputSection;
import net.yozmnews.report.domain.input.InterestsSection;
import net.yozmnews.report.domain.input.ReportInput;
import net.yozmnews.report.domain.input.StorylinesSection;
import net.yozmnews.report.domain.input.TopicInterestLine;
import net.yozmnews.report.domain.input.UserStorylineInterestLine;
import net.yozmnews.report.domain.input.UserStorylineMaterial;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineState;

/**
 * 리포트 시험의 값 — 갈래는 재료 꼴로 만든다. 갈래의 인용 대상은 재료에서 나온다(report-R14): 추적한 사용자 스토리라인은 헤드 → 그 사용자 스토리라인의 사건, 관심
 * 변화는 사용자 스토리라인 관심 줄의 헤드
 */
public final class ReportFixtures {

	/** 쓸 모델 */
	public static final LlmModel MODEL = new LlmModel(Provider.OPENAI, "gpt-5.5-mini", 100_000, true);

	/** 추론 강도 */
	public static final ReasoningEffort EFFORT = ReasoningEffort.MEDIUM;

	/** 통계 스냅숏 */
	public static final String SNAPSHOT = "{\"storylines\":{\"이번\":12,\"직전\":7}}";

	/** 입력의 사용자 */
	public static final UserId USER = new UserId(7);

	/** 입력의 기간 — 2026-11-16 ~ 11-23 주간 */
	public static final ReportPeriod PERIOD = new ReportPeriod(ReportPeriodKind.WEEK,
			Instant.parse("2026-11-16T00:00:00Z"), Instant.parse("2026-11-23T00:00:00Z"));

	/** 입력의 사용자 시간대 */
	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private ReportFixtures() {
	}

	public static ReportCitation.Storyline storyline(long id) {
		return new ReportCitation.Storyline(new StorylineId(id));
	}

	public static ReportCitation.Event event(long id) {
		return new ReportCitation.Event(new EventId(id));
	}

	/** 사용자 스토리라인 하나 — 헤드 하나가 꼭대기이고, 사건마다 사건 줄 하나 */
	public static UserStorylineMaterial userStoryline(long head, long... events) {
		List<EventLine> lines = Arrays.stream(events)
			.mapToObj(event -> new EventLine(new EventId(event), "사건 " + event, LocalDate.of(2026, 11, 17), 1, true))
			.toList();
		return new UserStorylineMaterial(new StorylineId(head), Set.of(new StorylineId(head)), null, StorylineState.ACTIVE, false,
				new Count(0, null), lines);
	}

	/** 추적한 사용자 스토리라인의 갈래 */
	public static StorylinesSection userStorylines(UserStorylineMaterial... userStorylines) {
		return new StorylinesSection(List.of(userStorylines));
	}

	/** 관심 변화의 갈래 — 헤드마다 사용자 스토리라인 관심 줄, 헤드가 없으면 주제 관심 줄 하나 */
	public static InterestsSection interests(long... heads) {
		if (heads.length == 0) {
			return new InterestsSection(
					List.of(new TopicInterestLine("economy.finance", "금융", 0.1, 0.3, InterestShift.ENTERED)), List.of(),
					List.of());
		}
		return new InterestsSection(List.of(),
				Arrays.stream(heads)
					.mapToObj(head -> new UserStorylineInterestLine(new StorylineId(head), null, 0.4, 0.6, InterestShift.NONE))
					.toList(),
				List.of());
	}

	/** {@link #USER} · {@link #PERIOD} 의 입력 */
	public static ReportInput input(InputSection... sections) {
		return input(USER, PERIOD, sections);
	}

	public static ReportInput input(UserId user, ReportPeriod period, InputSection... sections) {
		return new ReportInput(user, period, ZONE, List.of(sections), List.of(), SNAPSHOT, null);
	}

}
