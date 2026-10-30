package net.yozmnews.source.domain.poll;

import java.time.Instant;

import net.yozmnews.source.SourceId;
import org.jspecify.annotations.Nullable;

/**
 * 소스마다 다음 요청 가능 시각의 저장소 — docs/specs/source.md §4 · source-R18.
 * <p>
 * 두 메서드는 같은 트랜잭션 안에서 부른다 — 잠근 행을 읽고 새 값을 쓴다.
 */
public interface SourceFetchSlotRepository {

	/**
	 * 소스의 행을 잠그고 다음 요청 가능 시각을 읽는다.
	 * @return 아직 요청한 적이 없으면 null
	 */
	@Nullable Instant lockNextFetchAt(SourceId source);

	void saveNextFetchAt(SourceId source, Instant next);

}
