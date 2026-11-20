package net.yozmnews.feedback;

import java.util.Collection;
import java.util.Map;

import net.yozmnews.event.EventId;

/**
 * 지운 항목의 항목 기록 지우기 — {@code digest} 가 항목 섹션을 지우는 트랜잭션 안에서 부른다. docs/specs/feedback.md §3 · feedback-R5
 * <p>
 * 울타리를 배타로 잡고 지운다 — 진행 중인 기록이 먼저 커밋되고, 그 뒤에 시작한 기록은 지운 섹션을 찾지 못해 받지 않는다. 사건마다의 현재 상태 ·
 * 이력은 건드리지 않는다.
 */
public interface DigestItemRemoval {

	/**
	 * 주어진 다이제스트마다 그 항목 키의 항목 기록을 모든 사용자에 대해 지운다. 빈 입력이면 아무것도 하지 않는다. 다시 불러도 결과가 같다.
	 * @param itemKeysByDigest 다이제스트 ID → 지운 항목의 키(그 항목의 첫 사건)
	 */
	void forget(Map<Long, ? extends Collection<EventId>> itemKeysByDigest);

}
