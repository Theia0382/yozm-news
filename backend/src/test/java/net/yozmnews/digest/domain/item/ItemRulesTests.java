package net.yozmnews.digest.domain.item;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.user.SummaryLength;
import net.yozmnews.user.domain.interest.DigestVolume;
import org.junit.jupiter.api.Test;

/**
 * 주요 뉴스 항목 수의 위쪽과 공급자 한도 — 주요 뉴스 칸은 항목 별칭을 키로 한 필수 객체라 키가 많으면 공급자가 출력 스키마를 거부한다. 주요
 * 뉴스가 되는 항목의 요약문 길이 기준은 요약 길이 단계마다 주요 뉴스 요약 길이 기준의 하한이다. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R4 · digest-R47
 * <p>
 * 한도 20 은 손으로 적은 실측값이다 — 주요 뉴스 칸이 키 꼴(값은 제목 · 요약)이고 헤드라인 칸이 배열인 출력 스키마를 Anthropic 구조화 출력이
 * 키 20 개까지 받고 22 개에서 문법이 너무 크다고 거부했다. 공급자는 이 크기 한도의 수치를 공개하지 않는다.
 */
class ItemRulesTests {

	/** 주요 뉴스 칸의 키 수로 공급자가 받는 것을 잰 가장 큰 값 */
	private static final int MEASURED_KEY_LIMIT = 20;

	@Test
	void theMostTopStoriesFitTheKeysTheProviderAccepts() {
		assertThat(DigestVolume.MAX_BIG_SLOTS + ItemRules.TOP_STORIES).isLessThanOrEqualTo(MEASURED_KEY_LIMIT);
	}

	@Test
	void minSummaryLengthIsTheTopStorySummaryLowerBound() {
		// 요약 길이 단계마다 주요 뉴스 요약 길이 기준의 하한 — 짧게 100 · 보통 200 · 자세히 400
		assertThat(ItemRules.minSummaryLength(SummaryLength.SHORT)).isEqualTo(100);
		assertThat(ItemRules.minSummaryLength(SummaryLength.NORMAL)).isEqualTo(200);
		assertThat(ItemRules.minSummaryLength(SummaryLength.DETAILED)).isEqualTo(400);
	}

}
