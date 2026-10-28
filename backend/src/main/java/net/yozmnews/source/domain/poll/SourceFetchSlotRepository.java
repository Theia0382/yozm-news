package net.yozmnews.source.domain.poll;

import java.time.Instant;

import net.yozmnews.source.SourceId;
import org.jspecify.annotations.Nullable;

/**
 * 소스마다 다음 요청을 시작할 수 있는 시각 — 같은 소스의 요청 시작 사이를 띄우는 예약의 저장소. 예약 규칙은
 * {@link PollSchedule#reserveFetch} 다. 두 메서드는 같은 트랜잭션 안에서 부른다 — 잠근 행을 읽고 새 값을 쓴다.
 */
public interface SourceFetchSlotRepository {

	/** 소스의 행을 잠그고 다음 요청 가능 시각을 읽는다 — 아직 요청한 적이 없으면 null */
	@Nullable Instant lockNextFetchAt(SourceId source);

	void saveNextFetchAt(SourceId source, Instant next);

}
