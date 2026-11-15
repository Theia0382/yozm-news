package net.yozmnews.user.adapter.in.web;

import java.util.List;

import net.yozmnews.user.domain.preference.PreferenceSet;

/**
 * 태그 선호 — 키 순. docs/specs/user.md §3 · user-R45
 *
 * @param topics 구독한 주제(대분류 키 · 중분류 전체 키)
 * @param excludedRegions 제외한 지역
 * @param excludedFormats 제외한 형식
 */
record PreferencesResponse(List<String> topics, List<String> excludedRegions, List<String> excludedFormats) {

	static PreferencesResponse of(PreferenceSet preferences) {
		return new PreferencesResponse(preferences.topics().stream().sorted().toList(),
				preferences.excludedRegions().stream().sorted().toList(),
				preferences.excludedFormats().stream().sorted().toList());
	}

}
