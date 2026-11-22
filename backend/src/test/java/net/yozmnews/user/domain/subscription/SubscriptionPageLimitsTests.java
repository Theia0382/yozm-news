package net.yozmnews.user.domain.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.storyline.StorylineDirectory;
import org.junit.jupiter.api.Test;

/**
 * 구독 화면의 제목 붙이기 전제 — 구독 이력 한 쪽은 카드 처리기 한 번(스토리라인 여럿 찾기의 상한)으로 덮인다. 상수 값을 옮겨 적지 않고 정본
 * 둘을 견준다 — 한쪽을 바꿔 관계가 깨지면 여기서 실패한다. 추적 중인 흐름 목록은 줄이 추적 씨앗마다라 상한보다 많을 수 있어 화면이 나눠
 * 묻는다.
 * <p>
 * 지키는 규칙: user-R63 · reader-R6
 */
class SubscriptionPageLimitsTests {

	@Test
	void oneHistoryPageFitsOneCardRequest() {
		assertThat(StorylineSubscriptionHistoryPage.SIZE).isPositive()
			.isLessThanOrEqualTo(StorylineDirectory.MAX_PAGE_SIZE);
	}

}
