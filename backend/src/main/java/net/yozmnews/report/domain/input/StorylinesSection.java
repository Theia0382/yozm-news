package net.yozmnews.report.domain.input;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.ReportSectionKind;

/**
 * 추적한 사용자 스토리라인의 변화 갈래(`STORYLINES`) — 사용자 스토리라인마다 사용자 스토리라인 재료 · docs/specs/report.md §5 · report-R14 · report-R16
 *
 * @param userStorylines 사용자 스토리라인 — 헤드 ID 순. 비지 않는다
 */
public record StorylinesSection(List<UserStorylineMaterial> userStorylines) implements InputSection {

	/**
	 * @throws IllegalArgumentException 사용자 스토리라인이 없을 때 — 재료가 없는 갈래는 입력에 들지 않는다
	 */
	public StorylinesSection {
		userStorylines = List.copyOf(Objects.requireNonNull(userStorylines, "userStorylines"));
		if (userStorylines.isEmpty()) {
			throw new IllegalArgumentException("추적한 사용자 스토리라인의 갈래에 사용자 스토리라인이 없다");
		}
	}

	@Override
	public ReportSectionKind kind() {
		return ReportSectionKind.STORYLINES;
	}

	/** 사용자 스토리라인마다 헤드, 이어 그 사용자 스토리라인의 사건 줄의 사건(report-R14) */
	@Override
	public List<ReportCitation> targets() {
		Set<ReportCitation> targets = new LinkedHashSet<>();
		for (UserStorylineMaterial userStoryline : this.userStorylines) {
			targets.add(new ReportCitation.Storyline(userStoryline.head()));
			userStoryline.events().forEach(line -> targets.add(new ReportCitation.Event(line.event())));
		}
		return List.copyOf(new ArrayList<>(targets));
	}

}
