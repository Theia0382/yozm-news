package net.yozmnews.source;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 피드 건강 검사가 피드 상태를 바꿨다(source → user) — docs/specs/source.md §3.
 * <p>
 * 검사 회차 하나에 바뀐 피드가 있을 때만, 상태를 쓴 트랜잭션에서 한 번 발행한다. 무엇이 바뀌었는지(이전 → 새 상태)를 싣는다 — 받는 쪽이
 * 지금 상태를 다시 읽어서는 이전 상태를 알 수 없다. 같은 이벤트가 다시 처리되거나 회차의 처리 순서가 바뀔 수 있으므로, 받는 쪽은 판정
 * 시각으로 회차를 가린다.
 *
 * @param judgedAt 판정 시각 — 바뀐 피드에 저장한 "바뀐 시각"과 같다
 * @param transitions 바뀐 피드 — 피드 ID 순, 비지 않는다
 */
public record FeedStatusesChanged(Instant judgedAt, List<FeedStatusTransition> transitions) {

	public FeedStatusesChanged {
		Objects.requireNonNull(judgedAt, "judgedAt");
		transitions = List.copyOf(transitions);
		if (transitions.isEmpty()) {
			throw new IllegalArgumentException("바뀐 피드가 없다");
		}
	}

}
