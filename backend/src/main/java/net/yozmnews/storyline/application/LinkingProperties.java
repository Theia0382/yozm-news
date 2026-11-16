package net.yozmnews.storyline.application;

import java.time.Duration;

import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 잇기 기준 설정({@code yozmnews.storyline-linking}) — 기본값의 정본이다 · docs/specs/storyline.md §7.
 * <p>
 * 아래 기준 · 위 기준의 기본값은 띠가 빈 값(둘 다 0.86 — 점수만으로 정한다)이고, 시간 창 · 확률 기준의 기본값은 측정 전 목표다. 운영자는
 * 환경 변수({@code YOZMNEWS_STORYLINE_LINKING_LOWER} 식)로 바꾸고, 범위를 어기면 기동하지 않는다({@link LinkingCriteria}). 옛 키
 * {@code threshold} 가 설정돼 있으면 기동하지 않는다 — 조용히 무시하면 기준을 덮어쓴 인스턴스가 모르게 기본값으로 돌아간다.
 *
 * @param window 시간 창
 * @param lower 아래 기준
 * @param upper 위 기준
 * @param probability 확률 기준
 * @param threshold 옛 키 — 값이 있으면 기동을 거부한다
 */
@ConfigurationProperties("yozmnews.storyline-linking")
record LinkingProperties(@DefaultValue("14d") Duration window, @DefaultValue("0.86") double lower,
		@DefaultValue("0.86") double upper, @DefaultValue("0.50") double probability, @Nullable Double threshold) {

	/**
	 * @throws IllegalArgumentException 범위를 어기거나 옛 키가 있을 때
	 */
	LinkingCriteria criteria() {
		if (this.threshold != null) {
			throw new IllegalArgumentException("yozmnews.storyline-linking.threshold 는 더 쓰지 않는다 — 위 기준 upper · 아래 기준 "
					+ "lower · 확률 기준 probability 로 정한다(YOZMNEWS_STORYLINE_LINKING_UPPER · _LOWER · _PROBABILITY): "
					+ this.threshold);
		}
		return new LinkingCriteria(this.window, this.lower, this.upper, this.probability);
	}

}
