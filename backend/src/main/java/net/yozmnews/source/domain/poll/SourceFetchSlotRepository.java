package net.yozmnews.source.domain.poll;

import java.time.Instant;

import net.yozmnews.source.SourceId;

/**
 * 소스마다 다음 요청 가능 시각의 저장소 — docs/specs/source.md §4 · source-R18.
 * <p>
 * 두 메서드는 같은 트랜잭션 안에서 부른다 — 잠근 행을 읽고 새 값을 쓴다.
 */
public interface SourceFetchSlotRepository {

	/**
	 * 소스 행을 공유 잠금한 뒤 그 소스의 예약 행을 잠그고 다음 요청 가능 시각을 읽는다 — 소스 삭제와 줄을 선다.
	 * @return 소스가 없으면 {@link SlotLock.SourceGone}
	 */
	SlotLock lockNextFetchAt(SourceId source);

	void saveNextFetchAt(SourceId source, Instant next);

	/** 소스의 예약 행을 지운다 — 소스 삭제가 소스 행보다 먼저 부른다 */
	void deleteBySource(SourceId source);

}
