package net.yozmnews.digest.domain.item;

/**
 * 항목 가르기의 상수와 구독 행마다 상한의 산식 — 정본 · docs/specs/digest.md §5 · digest-R47 · digest-R54
 */
public final class ItemRules {

	/**
	 * 큰 슬롯 항목 밖에서 주요 뉴스로 두는 항목 수 — 측정 전 목표. 주요 뉴스는 큰 슬롯 항목(사용자의 큰 슬롯 수 이하, 그 상한은 {@code user}
	 * 의 큰 슬롯 수 상한 {@code DigestVolume.MAX_BIG_SLOTS} 10) 더하기 이 수까지다. 주요 뉴스 칸은 항목 별칭을 키로 한 필수 객체라 그 합이
	 * 공급자가 받는 키 수 안이어야 한다 — 이 값을 올리면 그 한도를 함께 본다(시험이 묶는다)
	 */
	public static final int TOP_STORIES = 5;

	/**
	 * 주요 뉴스 칸에서 구독 행 하나가 차지하는 몫(백분율) — 측정 전 목표. 한 구독 행의 상한({@link #topicCap})과 상한을 거는
	 * 조건({@link #topicCapApplies})을 이 값으로 센다
	 */
	public static final int TOPIC_SHARE_PERCENT = 40;

	private ItemRules() {
	}

	/**
	 * 한 구독 행이 차지하는 주요 뉴스 항목 수의 상한을 센다 — 칸 수(큰 슬롯 항목 수 + {@link #TOP_STORIES}) ×
	 * {@link #TOPIC_SHARE_PERCENT} ÷ 100 의 내림이다.
	 * @param bigItems 그날 큰 슬롯 항목 수
	 */
	static int topicCap(int bigItems) {
		return (bigItems + TOP_STORIES) * TOPIC_SHARE_PERCENT / 100;
	}

	/**
	 * 구독 행마다 상한을 거는지 판정한다 — 구독 행 수 × {@link #TOPIC_SHARE_PERCENT} 가 100 이상일 때다. 한 행이 칸의 그 몫까지라 그보다
	 * 적은 행으로는 칸을 다 채울 수 없다.
	 * @param rows 항목(큰 슬롯 항목 포함)에 나온 구독 행 수
	 */
	static boolean topicCapApplies(int rows) {
		return rows * TOPIC_SHARE_PERCENT >= 100;
	}

}
