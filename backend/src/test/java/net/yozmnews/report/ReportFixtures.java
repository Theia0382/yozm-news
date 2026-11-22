package net.yozmnews.report;

import java.util.List;

import net.yozmnews.event.EventId;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.report.domain.input.InputSection;
import net.yozmnews.report.domain.input.ReportInput;
import net.yozmnews.storyline.StorylineId;

/**
 * 리포트 시험의 값 — 입력 · 인용 대상 · 쓸 모델
 */
public final class ReportFixtures {

	/** 쓸 모델 */
	public static final LlmModel MODEL = new LlmModel(Provider.OPENAI, "gpt-5.5-mini", 100_000, true);

	/** 통계 스냅숏 */
	public static final String SNAPSHOT = "{\"coverage\":{\"이번\":12,\"직전\":7}}";

	private ReportFixtures() {
	}

	public static ReportCitation.Storyline storyline(long id) {
		return new ReportCitation.Storyline(new StorylineId(id));
	}

	public static ReportCitation.Event event(long id) {
		return new ReportCitation.Event(new EventId(id));
	}

	public static InputSection section(ReportSectionKind kind, ReportCitation... targets) {
		return new InputSection(kind, List.of(targets));
	}

	public static ReportInput input(InputSection... sections) {
		return new ReportInput(List.of(sections), SNAPSHOT);
	}

}
