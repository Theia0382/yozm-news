package net.yozmnews.storyline.application;

import java.time.Duration;

import net.yozmnews.storyline.domain.relation.RelationCriteria;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 관계 기준 설정({@code yozmnews.storyline-relation}) — 기본값의 정본이다 · docs/specs/storyline.md §7.
 * <p>
 * 기본값은 측정 전 목표다. 운영자는 환경 변수({@code YOZMNEWS_STORYLINE_RELATION_PROBABILITY} 식)로 바꾸고, 범위를 어기면 기동하지
 * 않는다({@link RelationCriteria}).
 *
 * @param probability 관계를 맺는 확률 기준
 * @param oneMatter 한 사안 기준
 * @param minEvents 후보가 되는 스토리라인의 구성 사건 수 하한
 * @param candidateScore 점수 후보의 기준
 * @param window 점수 후보를 찾고 말 수를 세는 시간 창
 */
@ConfigurationProperties("yozmnews.storyline-relation")
record RelationProperties(@DefaultValue("0.60") double probability, @DefaultValue("0.20") double oneMatter,
		@DefaultValue("3") int minEvents, @DefaultValue("0.88") double candidateScore,
		@DefaultValue("14d") Duration window) {

	/**
	 * @throws IllegalArgumentException 범위를 어길 때
	 */
	RelationCriteria criteria() {
		return new RelationCriteria(this.probability, this.oneMatter, this.minEvents, this.candidateScore, this.window);
	}

}
