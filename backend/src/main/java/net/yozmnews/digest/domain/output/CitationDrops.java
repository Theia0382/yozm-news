package net.yozmnews.digest.domain.output;

/**
 * 인용 검증이 지운 것의 수
 * @param removedRefs 주요 뉴스에서 지운 별칭 수 — 후보에 없거나 같은 주요 뉴스 안에서 거듭됨
 * @param droppedItems 버린 주요 뉴스 · 추가 헤드라인 수
 */
public record CitationDrops(int removedRefs, int droppedItems) {

	public CitationDrops {
		if (removedRefs < 0 || droppedItems < 0) {
			throw new IllegalArgumentException("음수다: " + removedRefs + ", " + droppedItems);
		}
	}

}
