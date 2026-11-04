package net.yozmnews.evaluation.domain.backfill;

/**
 * 채움 묶음 하나가 버전 하나에서 센 건수 — 진행 기록에 더한다 · docs/specs/evaluation.md §4
 *
 * @param processed 처리한 평가 수 — 건너뛴 평가 포함
 * @param tagsAdded 더한 태그 행 수
 * @param skippedOther 평가기가 달라 건너뛴 평가 수
 * @param skippedUnreadable 원본 응답을 읽을 수 없거나 체계와 어긋나 건너뛴 평가 수
 */
public record BackfillCounts(long processed, long tagsAdded, long skippedOther, long skippedUnreadable) {

	/** 두 건수를 더한다 */
	public BackfillCounts plus(BackfillCounts other) {
		return new BackfillCounts(this.processed + other.processed, this.tagsAdded + other.tagsAdded,
				this.skippedOther + other.skippedOther, this.skippedUnreadable + other.skippedUnreadable);
	}

}
