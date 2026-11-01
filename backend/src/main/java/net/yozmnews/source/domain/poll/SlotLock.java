package net.yozmnews.source.domain.poll;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * 소스의 다음 요청 가능 시각을 잠근 결과 — docs/specs/source.md §4 · source-R18.
 * <p>
 * 소스 행을 먼저 잠그므로, 잠그는 사이 소스가 지워졌으면 행이 없다는 결과가 된다.
 */
public sealed interface SlotLock {

	/**
	 * 잠갔다.
	 * @param nextFetchAt 다음 요청 가능 시각 — 아직 요청한 적이 없으면 null
	 */
	record Locked(@Nullable Instant nextFetchAt) implements SlotLock {
	}

	/** 소스가 없다 — 지워졌다 */
	record SourceGone() implements SlotLock {
	}

}
