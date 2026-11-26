package net.yozmnews.report.domain.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.report.domain.material.ReportSnapshot.SnapshotUserStoryline;
import net.yozmnews.storyline.StorylineId;
import org.junit.jupiter.api.Test;

/**
 * 직전 값 잇기 — 합쳐진 스토리라인 · 이어진 사용자 스토리라인 · 지운 스토리라인. 지키는 규칙: report-R18
 */
class PreviousMatchTests {

	@Test
	void userStorylinesCarryOnlyWhenOnePreviousUserStorylineMeetsOneCurrentUserStoryline() {
		StorylineId s10 = new StorylineId(10);
		StorylineId s11 = new StorylineId(11);
		StorylineId s20 = new StorylineId(20);
		StorylineId s21 = new StorylineId(21);
		StorylineId s30 = new StorylineId(30);
		StorylineId s40 = new StorylineId(40);
		StorylineId s41 = new StorylineId(41);
		// 직전: 10(그대로) · 11(지금 10 으로 합쳐짐) · 20(이어져 지금 사용자 스토리라인 21 의 꼭대기) · 30(지움) · 40 · 41(지금 사용자 스토리라인 하나)
		List<SnapshotUserStoryline> previous = List.of(new SnapshotUserStoryline(s20, Set.of(s20), 7),
				new SnapshotUserStoryline(s30, Set.of(s30), 3), new SnapshotUserStoryline(s40, Set.of(s40), 4),
				new SnapshotUserStoryline(s41, Set.of(s41), 6));
		Map<StorylineId, StorylineId> resolved = Map.of(s20, s20, s40, s40, s41, s41);
		Map<StorylineId, Set<StorylineId>> current = Map.of(s21, Set.of(s20, s21), s41, Set.of(s40, s41));

		Map<StorylineId, Long> carried = PreviousMatch.userStorylines(previous, resolved, current);

		// 이어져 헤드가 바뀐 사용자 스토리라인은 잇고(21 ← 20), 지운 것은 없고, 직전 둘이 지금 하나로 모인 사용자 스토리라인(41)은 비운다
		assertThat(carried).containsOnly(Map.entry(s21, 7L));

		// 합쳐진 스토리라인 — 직전 둘(10 · 11)이 지금 하나(10)로 풀리면 비우고, 하나만 있었으면 잇는다
		Map<StorylineId, StorylineId> merged = Map.of(s10, s10, s11, s10);
		Map<StorylineId, Set<StorylineId>> one = Map.of(s10, Set.of(s10));
		assertThat(PreviousMatch.userStorylines(List.of(new SnapshotUserStoryline(s10, Set.of(s10), 2), new SnapshotUserStoryline(s11, Set.of(s11), 5)),
				merged, one)).isEmpty();
		assertThat(PreviousMatch.userStorylines(List.of(new SnapshotUserStoryline(s11, Set.of(s11), 5)), merged, one))
			.containsOnly(Map.entry(s10, 5L));
		// 직전이 없으면 이을 것이 없다
		assertThat(PreviousMatch.userStorylines(List.of(), Map.of(), one)).isEmpty();
	}

}
