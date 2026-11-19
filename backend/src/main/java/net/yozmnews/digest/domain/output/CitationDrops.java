package net.yozmnews.digest.domain.output;

/**
 * 항목 대조가 지운 것의 수
 * @param droppedItems 버린 주요 뉴스 · 헤드라인 원소 수 — 모르는 항목 별칭 · 다른 칸의 항목 · 앞에서 이미 쓴 항목
 * @param missingItems 출력에 없어 저장하지 않는 항목 수
 */
public record CitationDrops(int droppedItems, int missingItems) {

	public CitationDrops {
		if (droppedItems < 0 || missingItems < 0) {
			throw new IllegalArgumentException("음수다: " + droppedItems + ", " + missingItems);
		}
	}

}
