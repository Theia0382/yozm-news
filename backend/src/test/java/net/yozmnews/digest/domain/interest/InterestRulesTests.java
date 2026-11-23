package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 관심도 상수의 성질 — 값 하나하나가 아니라 상수 사이의 관계를 지킨다.
 */
class InterestRulesTests {

	@Test
	void aTopicSubscriptionAloneStaysBelowThePresenceThreshold() {
		// 구독만 한 주제의 관심은 γ·1 — "관심 있음" 기준보다 작아야 구독 목록이 관심으로 뜨지 않는다
		assertThat(InterestRules.TOPIC.subscriptionSignal()).isLessThan(InterestRules.PRESENCE_THRESHOLD);
	}

}
