package net.yozmnews.user.domain.interest;

import java.util.List;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetId;

/**
 * 설정 행 저장 — 구현은 {@code adapter/out/persistence} 의 {@code JdbcInterestSettings} — docs/specs/user.md §4 · user-R43 ·
 * user-R44 · user-R46 · user-R47
 */
public interface InterestSettingsRepository {

	/** 그 사용자의 설정 행을 잠그고 읽는다({@code FOR UPDATE}) — 트랜잭션 안에서 부른다 */
	Optional<InterestSettings> lockByUser(UserId user);

	/** 그 사용자의 설정 행을 잠그지 않고 읽는다 — 쓰지 않는 소비자 조회가 부른다 */
	Optional<InterestSettings> findByUser(UserId user);

	/** 설정 행이 없을 때만 넣는다 — 이미 있으면(동시에 넣은 것 포함) 아무것도 하지 않는다 */
	boolean insertIfAbsent(InterestSettings settings);

	/**
	 * 잠근 행을 바꾸고 판을 올린다.
	 * @throws IllegalStateException 읽은 판의 행이 없을 때 — 잠근 뒤라면 일어나지 않는다
	 */
	void update(InterestSettings settings);

	/** 설정 행이 없는 계정 — 계정 ID 가 {@code afterUserId} 보다 큰 것부터 ID 순으로 {@code limit} 개까지 */
	List<UserId> withoutSettings(long afterUserId, int limit);

	/** 선호 버전이 {@code active} 가 아닌 사용자 — 사용자 ID 가 {@code afterUserId} 보다 큰 것부터 ID 순으로 {@code limit} 개까지 */
	List<UserId> withPreferenceVersionOtherThan(TagSetId active, long afterUserId, int limit);

}
