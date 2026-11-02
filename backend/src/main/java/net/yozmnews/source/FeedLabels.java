package net.yozmnews.source;

import java.util.Collection;
import java.util.Map;

/**
 * 다른 모듈이 피드의 출처 이름 · 섹션을 읽는 조회 — docs/specs/source.md §3.
 * <p>
 * 숨긴 소스 · 피드도 낸다. 읽기 전용 트랜잭션 하나에서 읽는다.
 */
public interface FeedLabels {

	/**
	 * 피드들의 출처 이름 · 섹션을 읽는다.
	 * @return 피드 ID → 이름 · 섹션 — 없는(지운) 피드는 결과에 없다. 빈 입력이면 빈 맵
	 */
	Map<FeedId, FeedLabel> labels(Collection<FeedId> feeds);

}
