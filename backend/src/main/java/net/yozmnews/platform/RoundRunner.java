package net.yozmnews.platform;

import java.util.function.Supplier;

/**
 * 회차 실행 — 뭉치를 회차 규칙까지 되풀이하고 회차 기록을 남긴다 · docs/specs/platform.md §3 · 반복 회차.
 * <p>
 * 트랜잭션을 열지 않는다 — 뭉치 안의 외부 호출은 뭉치가 정한 트랜잭션 경계 그대로다.
 */
public interface RoundRunner {

	/**
	 * 회차 하나를 돈다 — 뭉치가 {@link BatchEnd#FULL} 이고 회차를 시작한 지 시간 예산이 지나지 않았으면 다음 뭉치를 부른다.
	 * @param round 회차 이름 — 소문자 낱말을 {@code -} 로 이은 것(예 {@code storyline-linking})
	 * @param batch 뭉치 하나 — 예외를 던지면 회차 기록을 {@link RoundEnd#FAILED} 로 남기고 그 예외를 다시 던진다
	 * @throws IllegalArgumentException 회차 이름의 꼴이 틀렸을 때
	 */
	void run(String round, Supplier<RoundBatch> batch);

}
