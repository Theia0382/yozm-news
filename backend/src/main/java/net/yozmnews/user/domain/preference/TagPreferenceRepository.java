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

}
