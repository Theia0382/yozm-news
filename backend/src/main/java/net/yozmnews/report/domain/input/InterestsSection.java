package net.yozmnews.report.domain.input;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.ReportSectionKind;

/**
 * 관심 변화 갈래(`INTERESTS`) — 주제 · 흐름 · 구독 변경의 관심 줄 · docs/specs/report.md §5 · report-R14 · report-R17
 *
 * @param topics 주제 관심 줄 — 체계 순서
 * @param flows 흐름 관심 줄 — 헤드 ID 순
 * @param subscriptionChanges 구독 변경 줄 — 시각 순
 */
public record InterestsSection(List<TopicInterestLine> topics, List<FlowInterestLine> flows,
		List<TopicSubscriptionLine> subscriptionChanges) implements InputSection {

	/**
	 * @throws IllegalArgumentException 줄이 하나도 없을 때 — 재료가 없는 갈래는 입력에 들지 않는다
	 */
	public InterestsSection {
		topics = List.copyOf(Objects.requireNonNull(topics, "topics"));
		flows = List.copyOf(Objects.requireNonNull(flows, "flows"));
		subscriptionChanges = List.copyOf(Objects.requireNonNull(subscriptionChanges, "subscriptionChanges"));
		if (topics.isEmpty() && flows.isEmpty() && subscriptionChanges.isEmpty()) {
			throw new IllegalArgumentException("관심 변화의 갈래에 줄이 없다");
		}
	}

	@Override
	public ReportSectionKind kind() {
		return ReportSectionKind.INTERESTS;
	}

	/** 흐름 관심 줄의 헤드(report-R14) */
	@Override
	public List<ReportCitation> targets() {
		Set<ReportCitation> targets = new LinkedHashSet<>();
		this.flows.forEach(line -> targets.add(new ReportCitation.Storyline(line.head())));
		return List.copyOf(targets);
	}

}
