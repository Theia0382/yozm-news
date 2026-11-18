package net.yozmnews.user.domain.preference;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.tag.TagSetId;

/**
 * 저장된 선호 이력의 주제 행 하나 — 기록 당시 버전의 키 그대로. docs/specs/user.md §4 · user-R45
 *
 * @param key 대분류 키 또는 중분류 전체 키
 * @param version 기록 당시 버전
 * @param action 더함 · 뺌
 * @param cause 원인
 * @param occurredAt 기록한 시각
 */
public record RecordedTopicChange(String key, TagSetId version, PreferenceAction action, PreferenceCause cause,
		Instant occurredAt) {

	public RecordedTopicChange {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(cause, "cause");
		Objects.requireNonNull(occurredAt, "occurredAt");
	}

}
