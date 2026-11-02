package net.yozmnews.source.domain.attribution;

import java.time.Instant;
import java.util.Map;

import net.yozmnews.source.SourceId;

/**
 * 출처 이름 기록의 저장소 — docs/specs/source.md §4.
 * <p>
 * 출처 이름은 source 가 프로필에서 마지막으로 확인한 소스 이름이다. 프로필을 얻지 못해도 남아 있다.
 */
public interface SourceAttributionRepository {

	/**
	 * 소스의 이름을 기록한다 — 기록된 이름과 같으면 아무것도 바꾸지 않는다(기록 시각 포함). 여러 인스턴스가 같은 값으로 불러도 결과가
	 * 같다.
	 */
	void record(SourceId source, String name, Instant at);

	/** 기록된 이름 — 기록이 없는 소스는 빠진다 */
	Map<SourceId, String> findAll();

	/** 소스의 기록을 지운다 — 소스 삭제가 소스 행보다 먼저 부른다 */
	void delete(SourceId source);

}
