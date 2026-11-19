package net.yozmnews.digest.domain.selection;

import java.util.Objects;

import net.yozmnews.digest.SelectionReason;
import org.jspecify.annotations.Nullable;

/**
 * 고른 사건 하나와 그 선정 이유 — 후보 선별의 결과 · docs/specs/digest.md §5
 *
 * @param event 후보 사건
 * @param reason 선정 이유
 * @param bigRow 큰 슬롯 사건이 나온 큰 판정 행의 위치 — 큰 슬롯 사건({@link SelectionReason#MAJOR_STORYLINE})만 있다
 */
public record SelectedEvent(CandidateEvent event, SelectionReason reason, @Nullable Integer bigRow) {

	/**
	 * @throws IllegalArgumentException 큰 판정 행 위치가 큰 슬롯 사건에만 있지 않을 때
	 */
	public SelectedEvent {
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(reason, "reason");
		if ((reason == SelectionReason.MAJOR_STORYLINE) != (bigRow != null)) {
			throw new IllegalArgumentException("큰 판정 행 위치는 큰 슬롯 사건에만 있다: " + reason + " · " + bigRow);
		}
	}

}
