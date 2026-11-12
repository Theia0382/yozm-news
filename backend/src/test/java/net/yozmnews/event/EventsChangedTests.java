package net.yozmnews.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 사건 변화 알림과 잇기의 재료 타입 — 알림은 비어 있지 않고 같은 사건이 두 번 없으며, 핵심 말은 비지 않고 수가 1 이상이고, 사건 표현의 중심은
 * 복사해 받고 복사해 내며, 말 수는 음수가 아니고 맵을 복사해 받는다.
 * <p>
 * 지키는 규칙: event-R23 · event-R24
 */
class EventsChangedTests {

	private static final Instant AT = Instant.parse("2026-11-01T00:00:00Z");

	@Test
	void aNotificationHasDistinctEventsAndIsNotEmpty() {
		List<EventId> ids = new ArrayList<>(List.of(new EventId(2), new EventId(1)));
		EventsChanged changed = new EventsChanged(ids);
		ids.add(new EventId(3));

		assertThat(changed.eventIds()).containsExactly(new EventId(2), new EventId(1));
		assertThatIllegalArgumentException().isThrownBy(() -> new EventsChanged(List.of()));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EventsChanged(List.of(new EventId(1), new EventId(2), new EventId(1))));
	}

	@Test
	void aKeyTermIsNotBlankAndCountsAtLeastOneArticle() {
		assertThat(new EventKeyTerm("금리", 1).articleCount()).isEqualTo(1);
		assertThatIllegalArgumentException().isThrownBy(() -> new EventKeyTerm("", 1));
		assertThatIllegalArgumentException().isThrownBy(() -> new EventKeyTerm("금리", 0));
	}

	@Test
	void aProfileCopiesItsCentroidInAndOut() {
		float[] centroid = { 1f, 0.5f };
		EventProfile profile = new EventProfile(new EventId(1), EventStatus.OPEN, "model-a", centroid, 2, AT, AT,
				List.of(new EventKeyTerm("금리", 2)));
		centroid[0] = 9f;
		profile.centroid()[1] = 9f;

		assertThat(profile.centroid()).containsExactly(1f, 0.5f);
	}

	@Test
	void termCountsAreNotNegativeAndCopyTheirMap() {
		Map<String, Long> byTerm = new HashMap<>(Map.of("금리", 2L, "없는말", 0L));
		EventTermCounts counts = new EventTermCounts(0, byTerm);
		byTerm.put("환율", 1L);

		assertThat(counts.events()).isZero();
		assertThat(counts.eventsByTerm()).containsOnly(Map.entry("금리", 2L), Map.entry("없는말", 0L));
		assertThatIllegalArgumentException().isThrownBy(() -> new EventTermCounts(-1, Map.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new EventTermCounts(3, Map.of("금리", -1L)));
	}

}
