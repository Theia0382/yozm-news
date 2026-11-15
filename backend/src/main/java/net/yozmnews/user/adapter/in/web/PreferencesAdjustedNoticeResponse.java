package net.yozmnews.user.adapter.in.web;

import java.util.List;

import net.yozmnews.tag.TagKind;
import net.yozmnews.user.domain.notice.PreferencesAdjustedContent;
import net.yozmnews.user.domain.preference.AdjustmentKind;
import net.yozmnews.user.domain.preference.PreferenceMode;

/**
 * 선호 조정 알림의 본문 — 따라간 활성 버전 번호와 조정 항목. docs/specs/user.md §3 · user-R48
 *
 * @param tagSetNumber 따라간 활성 버전의 번호
 * @param adjustments 조정 항목 — 해제 다음 더함
 */
record PreferencesAdjustedNoticeResponse(int tagSetNumber, List<AdjustmentResponse> adjustments) {

	static PreferencesAdjustedNoticeResponse of(PreferencesAdjustedContent content) {
		return new PreferencesAdjustedNoticeResponse(content.tagSetNumber(),
				content.adjustments()
					.stream()
					.map(adjustment -> new AdjustmentResponse(adjustment.kind(), adjustment.key(), adjustment.name(),
							adjustment.mode(), adjustment.change()))
					.toList());
	}

	/**
	 * 조정 항목 하나 — 해제는 옛 버전의 키 · 이름, 더함은 활성 버전의 키 · 이름.
	 *
	 * @param kind 태그 종류
	 * @param key 태그 키
	 * @param name 그 버전의 태그 이름
	 * @param mode 구독 · 제외
	 * @param change 조정 종류
	 */
	record AdjustmentResponse(TagKind kind, String key, String name, PreferenceMode mode, AdjustmentKind change) {
	}

}
