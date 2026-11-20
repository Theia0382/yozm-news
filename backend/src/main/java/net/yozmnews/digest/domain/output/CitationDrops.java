package net.yozmnews.digest.domain.output;

/**
 * 항목 대조가 대체한 것의 수
 * @param substituted 출력에 칸이 없어 대체 헤드라인으로 남긴 항목 수
 */
public record CitationDrops(int substituted) {

	public CitationDrops {
		if (substituted < 0) {
			throw new IllegalArgumentException("음수다: " + substituted);
		}
	}

}
