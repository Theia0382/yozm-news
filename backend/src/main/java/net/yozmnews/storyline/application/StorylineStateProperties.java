package net.yozmnews.storyline.application;

import java.time.Duration;

import net.yozmnews.storyline.domain.storyline.StateCriteria;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 상태 기간 설정({@code yozmnews.storyline-state}) — 기본값의 정본이다 · docs/specs/storyline.md §7.
 * <p>
 * 기본값은 측정 전 목표다. 운영자는 환경 변수({@code YOZMNEWS_STORYLINE_STATE_QUIET_AFTER} 식)로 바꾸고, 범위를 어기면 기동하지
 * 않는다({@link StateCriteria}).
 *
 * @param quietAfter 잠잠 기간
 * @param endedAfter 끝남 기간
 */
@ConfigurationProperties("yozmnews.storyline-state")
record StorylineStateProperties(@DefaultValue("7d") Duration quietAfter, @DefaultValue("30d") Duration endedAfter) {

	/**
	 * @throws IllegalArgumentException 범위를 어길 때
	 */
	StateCriteria criteria() {
		return new StateCriteria(this.quietAfter, this.endedAfter);
	}

}
