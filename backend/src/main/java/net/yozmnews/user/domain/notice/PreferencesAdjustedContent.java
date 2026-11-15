package net.yozmnews.user.domain.notice;

import java.util.List;
import java.util.Objects;

import net.yozmnews.user.domain.preference.Adjustment;

/**
 * 선호 조정 알림의 내용 — 활성 버전 번호와 따라가기가 해제하거나 더한 항목. 모으지 않는다(사용자 · 활성 전환마다 알림 하나).
 * docs/specs/user.md §4 · user-R48
 *
 * @param tagSetNumber 따라간 활성 버전의 번호
 * @param adjustments 조정 항목 — 해제 다음 더함, 비지 않는다
 */
public record PreferencesAdjustedContent(int tagSetNumber, List<Adjustment> adjustments) implements NoticeContent {

	/**
	 * @throws IllegalArgumentException 조정 항목이 없을 때 — 조정이 없으면 알림을 남기지 않는다
	 */
	public PreferencesAdjustedContent {
		adjustments = List.copyOf(Objects.requireNonNull(adjustments, "adjustments"));
		if (adjustments.isEmpty()) {
			throw new IllegalArgumentException("조정 항목이 없는 선호 조정 알림이다");
		}
	}

}
