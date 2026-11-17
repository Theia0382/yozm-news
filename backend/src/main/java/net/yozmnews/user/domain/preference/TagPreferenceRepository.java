package net.yozmnews.user.domain.preference;

import java.time.Instant;
import java.util.List;

import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetId;

/**
 * 태그 선호의 현재 상태 · 이력 저장 — 구현은 {@code adapter/out/persistence} 의 {@code JdbcTagPreferences} — docs/specs/user.md §4 ·
 * user-R45
 */
public interface TagPreferenceRepository {

	/** 그 사용자의 현재 선호 */
	PreferenceSet load(UserId user);

	/**
	 * 차이를 현재 상태에 반영하고 변경마다 이력 한 행을 더한다 — 부르는 쪽 트랜잭션 안에서.
	 * @param version 이력 행에 적을 기록 당시 버전
	 */
	void apply(UserId user, List<PreferenceChange> changes, PreferenceCause cause, TagSetId version, Instant now);

	/**
	 * 현재 상태를 {@code next} 로 바꾼다 — 이력은 남기지 않는다(따라가기가 키를 활성 버전으로 옮길 때 — 이력은 {@link #record} 로 따로).
	 * 부르는 쪽 트랜잭션 안에서.
	 */
	void replace(UserId user, PreferenceSet next, Instant now);

	/**
	 * 이력 행만 더한다 — 현재 상태는 건드리지 않는다. 부르는 쪽 트랜잭션 안에서.
	 * @param version 이력 행에 적을 기록 당시 버전
	 */
	void record(UserId user, List<PreferenceChange> changes, PreferenceCause cause, TagSetId version, Instant now);

	/** 이력의 주제 행 가운데 시각이 {@code from} 이상 · {@code to} 미만인 것 — 시각 · 행 ID 순 */
	List<RecordedTopicChange> topicChanges(UserId user, Instant from, Instant to);

}
