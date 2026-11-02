package net.yozmnews.source;

import java.util.Objects;

/**
 * 운영자가 소스를 지웠다(source → article) — docs/specs/source.md §3.
 * <p>
 * 소스 · 그 피드 · 수집 상태 · 요청 예약 · 출처 이름 행을 지운 트랜잭션에서 발행한다. 소비자는 그 소스의 기록을 지운다.
 */
public record SourceDeleted(SourceId sourceId) {

	public SourceDeleted {
		Objects.requireNonNull(sourceId, "sourceId");
	}

}
