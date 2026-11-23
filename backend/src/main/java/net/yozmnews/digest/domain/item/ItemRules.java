package net.yozmnews.digest.domain.item;

/**
 * 항목 가르기의 상수 — 정본 · docs/specs/digest.md §5 · digest-R47 · digest-R54
 */
public final class ItemRules {

	/**
	 * 큰 슬롯 항목 밖에서 주요 뉴스로 두는 항목 수 — 측정 전 목표. 주요 뉴스는 큰 슬롯 항목(사용자의 큰 슬롯 수 이하, 그 상한은 {@code user}
	 * 의 큰 슬롯 수 상한 {@code DigestVolume.MAX_BIG_SLOTS} 10) 더하기 이 수까지다. 주요 뉴스 칸은 항목 별칭을 키로 한 필수 객체라 그 합이
	 * 공급자가 받는 키 수 안이어야 한다 — 이 값을 올리면 그 한도를 함께 본다(시험이 묶는다)
	 */
	public static final int TOP_STORIES = 5;

	/**
	 * 큰 슬롯 밖 주요 뉴스에서 구독 행 하나가 차지하는 항목 수의 상한 — 측정 전 목표. 큰 슬롯 밖 항목에 나온 구독 행 수 × 이 값이
	 * {@link #TOP_STORIES} 보다 작으면 걸지 않는다
	 */
	public static final int TOP_STORIES_PER_TOPIC = 2;

	private ItemRules() {
	}

}
