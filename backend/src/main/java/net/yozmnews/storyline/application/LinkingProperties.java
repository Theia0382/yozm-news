package net.yozmnews.storyline.application;

import java.time.Duration;

import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 잇기 기준 설정({@code yozmnews.storyline-linking}) — 기본값의 정본이다 · docs/specs/storyline.md §7.
 * <p>
 * 기본값은 측정 전 목표다. 운영자는 환경 변수({@code YOZMNEWS_STORYLINE_LINKING_THRESHOLD} 식)로 바꾸고, 범위를 어기면 기동하지
 * 않는다({@link LinkingCriteria}).
 *
 * @param window 시간 창
 * @param threshold 기준 점수
 */
@ConfigurationProperties("yozmnews.storyline-linking")
record LinkingProperties(@DefaultValue("14d") Duration window, @DefaultValue("0.65") double threshold) {

	/**
	 * @throws IllegalArgumentException 범위를 어길 때
	 */
	LinkingCriteria criteria() {
		return new LinkingCriteria(this.window, this.threshold);
	}

}
