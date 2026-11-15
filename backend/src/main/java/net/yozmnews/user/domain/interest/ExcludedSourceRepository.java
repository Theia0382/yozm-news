package net.yozmnews.user.domain.interest;

import java.time.Instant;
import java.util.Set;

import net.yozmnews.platform.UserId;
import net.yozmnews.source.SourceId;

/**
 * 제외 소스 저장 — 구현은 {@code adapter/out/persistence} 의 {@code JdbcExcludedSources} — docs/specs/user.md §4 · user-R49
 */
public interface ExcludedSourceRepository {

	/** 그 사용자의 제외 소스 */
	Set<SourceId> load(UserId user);

	/** 그 사용자의 제외 소스를 통째로 바꾼다 — 부르는 쪽 트랜잭션 안에서 */
	void replace(UserId user, Set<SourceId> excluded, Instant now);

	/** 그 소스의 제외 행을 모두 지운다 — 지운 행 수 */
	int forget(SourceId source);

}
