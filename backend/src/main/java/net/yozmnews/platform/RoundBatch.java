package net.yozmnews.platform;

import java.util.Objects;

/**
 * 뭉치 하나를 처리한 결과 — docs/specs/platform.md §3 · 반복 회차.
 * <p>
 * 회차는 팩토리 셋으로 끝난 꼴을 고른다.
 *
 * @param handled 이 뭉치가 가져와 처리한 항목 수
 * @param end 뭉치가 끝난 꼴
 */
public record RoundBatch(int handled, BatchEnd end) {

	/**
	 * @throws IllegalArgumentException 처리 수가 음수일 때
	 */
	public RoundBatch {
		if (handled < 0) {
			throw new IllegalArgumentException("처리 수는 음수가 아니다: " + handled);
		}
		Objects.requireNonNull(end, "end");
	}

	/**
	 * 멈춤 · 일시 실패 없이 마쳤다 — 처리 수가 상한 이상이면 {@link BatchEnd#FULL}, 아니면 {@link BatchEnd#DRAINED}.
	 * @param limit 뭉치 상한 — 1 이상
	 * @throws IllegalArgumentException 상한이 1 미만일 때
	 */
	public static RoundBatch finished(int handled, int limit) {
		if (limit < 1) {
			throw new IllegalArgumentException("뭉치 상한은 1 이상이다: " + limit);
		}
		return new RoundBatch(handled, (handled >= limit) ? BatchEnd.FULL : BatchEnd.DRAINED);
	}

	/** 일시 실패가 하나라도 났다 */
	public static RoundBatch backedOff(int handled) {
		return new RoundBatch(handled, BatchEnd.BACKED_OFF);
	}

	/** 회차를 멈추는 경우로 끝났다 */
	public static RoundBatch stopped(int handled) {
		return new RoundBatch(handled, BatchEnd.STOPPED);
	}

}
