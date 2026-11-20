package net.yozmnews.digest.domain.item;

/**
 * 항목 가르기의 상수 — 정본 · docs/specs/digest.md §5 · digest-R47
 */
public final class ItemRules {

	/**
	 * 큰 슬롯 항목 밖에서 주요 뉴스로 두는 항목 수 — 측정 전 목표. 주요 뉴스는 큰 슬롯 항목(사용자의 큰 슬롯 수 이하, 그 상한은 {@code user}
	 * 의 큰 슬롯 수 상한 {@code DigestVolume.MAX_BIG_SLOTS} 10) 더하기 이 수까지다
	 */
	public static final int TOP_STORIES = 5;

	private ItemRules() {
	}

}
