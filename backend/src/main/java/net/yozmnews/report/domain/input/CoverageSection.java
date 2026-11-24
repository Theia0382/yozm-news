package net.yozmnews.report.domain.input;

import java.util.List;
import java.util.Objects;

import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.ReportSectionKind;

/**
 * 구독 주제의 보도량 갈래(`COVERAGE`) — 구독 행마다 기간 보도량과 직전 값 · docs/specs/report.md §5 · report-R14
 *
 * @param topics 구독 행 — 키 순. 비지 않는다
 */
public record CoverageSection(List<TopicCoverageLine> topics) implements InputSection {

	/**
	 * @throws IllegalArgumentException 구독 행이 없을 때 — 재료가 없는 갈래는 입력에 들지 않는다
	 */
	public CoverageSection {
		topics = List.copyOf(Objects.requireNonNull(topics, "topics"));
		if (topics.isEmpty()) {
			throw new IllegalArgumentException("구독 주제 보도량의 갈래에 구독 행이 없다");
		}
	}

	@Override
	public ReportSectionKind kind() {
		return ReportSectionKind.COVERAGE;
	}

	/** 인용 대상이 없다 — 주제는 인용하지 않는다(report-R14) */
	@Override
	public List<ReportCitation> targets() {
		return List.of();
	}

}
