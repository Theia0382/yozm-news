package net.yozmnews.digest.domain.item;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.user.domain.interest.DigestVolume;
import org.junit.jupiter.api.Test;

/**
 * 주요 뉴스 항목 수의 위쪽과 공급자 한도, 구독 행마다 상한의 산식 — 주요 뉴스 칸은 항목 별칭을 키로 한 필수 객체라 키가 많으면 공급자가 출력
 * 스키마를 거부한다. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R4 · digest-R47 · digest-R54
 * <p>
 * 한도 20 은 손으로 적은 실측값이다 — 주요 뉴스 칸이 키 꼴(값은 제목 · 요약)이고 헤드라인 칸이 배열인 출력 스키마를 Anthropic 구조화 출력이
 * 키 20 개까지 받고 22 개에서 문법이 너무 크다고 거부했다. 공급자는 이 크기 한도의 수치를 공개하지 않는다. 상한의 기대값은 칸 수 × 40% 를 손으로
 * 내림한 값이다.
 */
class ItemRulesTests {

	/** 주요 뉴스 칸의 키 수로 공급자가 받는 것을 잰 가장 큰 값 */
	private static final int MEASURED_KEY_LIMIT = 20;

	@Test
	void theMostTopStoriesFitTheKeysTheProviderAccepts() {
		assertThat(DigestVolume.MAX_BIG_SLOTS + ItemRules.TOP_STORIES).isLessThanOrEqualTo(MEASURED_KEY_LIMIT);
	}

	@Test
	void theTopicCapIsFortyPercentOfTheSeatsRoundedDown() {
		// 칸 5 · 6 · 7 · 8 · 10 · 15 → 2 · 2 · 2 · 3 · 4 · 6
		assertThat(ItemRules.topicCap(0)).isEqualTo(2);
		assertThat(ItemRules.topicCap(1)).isEqualTo(2);
		assertThat(ItemRules.topicCap(2)).isEqualTo(2);
		assertThat(ItemRules.topicCap(3)).isEqualTo(3);
		assertThat(ItemRules.topicCap(5)).isEqualTo(4);
		assertThat(ItemRules.topicCap(DigestVolume.MAX_BIG_SLOTS)).isEqualTo(6);
	}

	@Test
	void theCapIsOnFromThreeRows() {
		assertThat(ItemRules.topicCapApplies(2)).isFalse();
		assertThat(ItemRules.topicCapApplies(3)).isTrue();
	}

}
