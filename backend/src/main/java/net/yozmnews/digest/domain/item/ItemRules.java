package net.yozmnews.digest.domain.item;

import net.yozmnews.digest.domain.output.DigestOutputSchema;

/**
 * 항목 가르기의 상수 — 정본 · docs/specs/digest.md §5 · digest-R47
 */
public final class ItemRules {

	/**
	 * 큰 슬롯 항목 밖에서 주요 뉴스로 두는 항목 수 — 측정 전 목표. 주요 뉴스는 큰 슬롯 항목(사용자의 큰 슬롯 수 이하, 그 상한은 {@code user}
	 * 의 큰 슬롯 수 상한 {@code DigestVolume.MAX_BIG_SLOTS} 10) 더하기 이 수까지라, 출력 서술의 주요 뉴스 상한
	 * {@link DigestOutputSchema#MAX_TOP_STORIES} 가 그 합 이상이어야 한다 — 이 값을 올리면 그 상한을 함께 본다. 넘친 항목은 출력 읽기가
	 * 버린다
	 */
	public static final int TOP_STORIES = 5;

	private ItemRules() {
	}

}
