package net.yozmnews.evaluation.domain.threshold;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.ThresholdChangeView;
import net.yozmnews.evaluation.ThresholdCondition;
import org.junit.jupiter.api.Test;

/**
 * 어긋난 상태와 저장 기준이 바뀐 적이 있는가 — 값 리터럴로 상태 집합을 본다. 인자는 태그 기준 / 저장 기준 / 지난 저장 기준 최댓값 순이다.
 * <p>
 * 지키는 규칙: evaluation-R34
 */
class ThresholdStandingTests {

	private static final Instant AT = Instant.parse("2026-11-04T00:00:00Z");

	@Test
	void aConsistentStateHasNoCondition() {
		assertThat(of("0.5", "0.1", "0.1", false)).isEmpty();
	}

	@Test
	void aTagThresholdBelowTheStoreThresholdIsAStandingCondition() {
		assertThat(of("0.05", "0.1", "0.1", false)).containsExactly(ThresholdCondition.TAG_BELOW_STORE);
		// 경계: 같은 값은 낮은 것이 아니다
		assertThat(of("0.1", "0.1", "0.1", false)).isEmpty();
	}

	@Test
	void aTagThresholdBelowAHigherPastStoreThresholdIsAStandingCondition() {
		assertThat(of("0.15", "0.1", "0.2", false)).containsExactly(ThresholdCondition.TAG_BELOW_PAST_STORE);
		// 경계: 지난 최댓값과 같은 태그 기준은 낮은 것이 아니다
		assertThat(of("0.2", "0.1", "0.2", false)).isEmpty();
	}

	@Test
	void aPastMaximumNoHigherThanTheCurrentStoreThresholdIsLeftToTagBelowStore() {
		// 지난 최댓값이 지금 저장 기준과 같으면 TAG_BELOW_STORE 만 — 같은 까닭을 두 번 알리지 않는다
		assertThat(of("0.05", "0.2", "0.2", false)).containsExactly(ThresholdCondition.TAG_BELOW_STORE);
		// 대조: 지난 최댓값이 더 높으면 둘 다다
		assertThat(of("0.05", "0.1", "0.2", false)).containsExactlyInAnyOrder(ThresholdCondition.TAG_BELOW_STORE,
				ThresholdCondition.TAG_BELOW_PAST_STORE);
	}

	@Test
	void aChangedStoreThresholdIsAStandingCondition() {
		assertThat(of("0.5", "0.1", "0.1", true)).containsExactly(ThresholdCondition.STORE_THRESHOLD_CHANGED);
	}

	@Test
	void theStoreThresholdChangedOnlyWhenAHistoryRowOfThatKindChangedIt() {
		ThresholdChangeView initialTopic = change(MultiTagKind.TOPIC, null, null, "0.5", "0.1");
		ThresholdChangeView topicTagOnly = change(MultiTagKind.TOPIC, "0.5", "0.1", "0.6", "0.1000");
		ThresholdChangeView regionStore = change(MultiTagKind.REGION, "0.5", "0.1", "0.5", "0.2");

		// 처음 값 행과 태그 기준만 바꾼 행(크기가 같은 저장 기준)은 바뀐 것이 아니다
		assertThat(ThresholdStanding.storeChanged(List.of(initialTopic, topicTagOnly), MultiTagKind.TOPIC)).isFalse();
		// 다른 태그 종류의 변경은 세지 않는다
		assertThat(ThresholdStanding.storeChanged(List.of(initialTopic, regionStore), MultiTagKind.TOPIC)).isFalse();
		assertThat(ThresholdStanding.storeChanged(List.of(initialTopic, regionStore), MultiTagKind.REGION)).isTrue();
	}

	private static Set<ThresholdCondition> of(String tag, String store, String pastMax,
			boolean storeChanged) {
		return ThresholdStanding.of(new ThresholdValues(new BigDecimal(tag), new BigDecimal(store)),
				new BigDecimal(pastMax), storeChanged);
	}

	private static ThresholdChangeView change(MultiTagKind kind, String previousTag, String previousStore, String tag,
			String store) {
		return new ThresholdChangeView(kind, (previousTag == null) ? null : new BigDecimal(previousTag),
				(previousStore == null) ? null : new BigDecimal(previousStore), new BigDecimal(tag),
				new BigDecimal(store), ChangeActor.OPERATOR, AT);
	}

}
