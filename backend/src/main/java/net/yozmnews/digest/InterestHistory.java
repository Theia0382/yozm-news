package net.yozmnews.digest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import net.yozmnews.platform.UserId;

/**
 * 관심사 히스토리 — 기준 시각마다 다이제스트와 같은 산식으로 계산한 주제 관심과 그 변화. 저장하지 않고 부를 때 계산한다.
 * docs/specs/digest.md §3 · digest-R34 ~ digest-R37
 * <p>
 * 사용자를 인자로 받는다 — 화면 경로에서는 세션 사용자로만 부른다. 트랜잭션 없이 다른 모듈의 조회를 차례로 부르고, 조회 사이의 태그 체계 활성
 * 전환으로 키 버전이 어긋나면 처음부터 다시 한다(끝내 어긋나면 {@link IllegalStateException}).
 */
public interface InterestHistory {

	/**
	 * 최근 기간별 관심 — 사용자 시간대에서 주는 월요일, 월은 1일에 시작하고 지금을 담은 기간이 마지막이다.
	 * @return 계정이 없으면 빈 값
	 */
	Optional<InterestTimeline> recent(UserId user, InterestHistorySpan span);

	/**
	 * 부르는 쪽이 정한 기준 시각들의 관심 — 맨 앞 기준 시각은 둘째의 변화를 정하는 견줌용이라 결과에 없다.
	 * @param references 기준 시각 — 둘 이상, 시간 순(같은 시각 허용 — 그 사이는 변화 · 구독 변경이 없다)
	 * @return 둘째부터의 기준 시각마다 하나. 계정이 없으면 빈 값
	 * @throws IllegalArgumentException 기준 시각이 둘보다 적거나 시간 순이 아닐 때(앞이 뒤보다 늦을 때)
	 */
	Optional<List<InterestPoint>> at(UserId user, List<Instant> references);

}
