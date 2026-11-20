package net.yozmnews.feedback.domain.record;

/**
 * 기록과 삭제(지운 사건 · 지운 항목 따르기)의 울타리 — 트랜잭션 advisory 잠금. 기록은 공유, 삭제는 배타로 들어간다. 둘 다 트랜잭션 안에서
 * 부르고 트랜잭션이 끝나면 풀린다. 구현은 {@code adapter/out/persistence} 의 {@code JdbcRemovalFence}. docs/specs/feedback.md §4 ·
 * feedback-R6
 */
public interface RemovalFence {

	/** 공유로 들어간다 — 기록이 항목과 지금 있는 사건을 확인하기 전에 부른다 */
	void enterShared();

	/** 배타로 들어간다 — 지운 사건 · 지운 항목의 행을 지우기 전에 부른다. 공유로 들어간 기록이 모두 끝날 때까지 기다린다. */
	void enterExclusive();

}
