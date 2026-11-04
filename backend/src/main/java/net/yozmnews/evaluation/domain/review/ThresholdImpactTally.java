package net.yozmnews.evaluation.domain.review;

import java.util.List;
import java.util.Objects;

import net.yozmnews.evaluation.EvaluationOverview;
import net.yozmnews.evaluation.EvaluationPeriod;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagProbability;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.domain.overview.EvaluationSample;
import net.yozmnews.evaluation.domain.overview.OverviewTally;
import net.yozmnews.evaluation.domain.result.TagJudgement.Judged;
import net.yozmnews.evaluation.domain.threshold.StoreFloors;

/**
 * 기준 미리보기 집계의 정본 — 같은 평가를 지금 기준 · 제안 기준의 평가 현황 집계 둘에 넣고, 그 태그 종류의 판정이 다른 평가를 센다 ·
 * docs/specs/evaluation.md §4 · evaluation-R33.
 * <p>
 * 판정은 두 집계가 한다({@link OverviewTally#add} 가 돌려준다). 주제는 주제 판정과 붙은 중분류 키, 지역은 붙은 지역 키를 견준다 — 체계 순
 * 목록이라 같은 집합이면 같은 목록이다. 메모리는 평가 수에 비례하지 않는다.
 */
public final class ThresholdImpactTally {

	private final MultiTagKind kind;

	private final OverviewTally before;

	private final OverviewTally after;

	private long changed;

	/**
	 * @param current 그 버전의 지금 태그 기준 · 저장 기준
	 * @param proposed 그 태그 종류만 제안 값으로 바꾼 기준
	 * @param topicFloors 그 버전 주제의 저장 바닥 — 지금 이력과 끝난 채움의 덮음, 두 집계가 함께 쓴다(제안을 이력에 더하지 않는다)
	 * @param regionFloors 그 버전 지역의 저장 바닥 — 두 집계가 함께 쓴다
	 * @throws IllegalArgumentException 기준의 버전이 체계와 다를 때
	 */
	public ThresholdImpactTally(TagSetView tagSet, TagSetThresholds current, TagSetThresholds proposed,
			MultiTagKind kind, StoreFloors topicFloors, StoreFloors regionFloors, EvaluationPeriod period) {
		this.kind = Objects.requireNonNull(kind, "kind");
		this.before = new OverviewTally(tagSet, current, topicFloors, regionFloors, period);
		this.after = new OverviewTally(tagSet, proposed, topicFloors, regionFloors, period);
	}

	/** 평가 한 건을 두 기준으로 센다 */
	public void add(EvaluationSample sample) {
		Judged judgedBefore = this.before.add(sample);
		Judged judgedAfter = this.after.add(sample);
		if (differs(judgedBefore, judgedAfter)) {
			this.changed++;
		}
	}

	/** 그 태그 종류의 판정이 두 기준에서 다른 평가의 수 */
	public long changed() {
		return this.changed;
	}

	/** 지금 기준의 평가 현황 */
	public EvaluationOverview before() {
		return this.before.overview();
	}

	/** 제안 기준의 평가 현황 */
	public EvaluationOverview after() {
		return this.after.overview();
	}

	private boolean differs(Judged before, Judged after) {
		return switch (this.kind) {
			case TOPIC -> before.status() != after.status() || !keys(before.topics()).equals(keys(after.topics()));
			case REGION -> !keys(before.regions()).equals(keys(after.regions()));
		};
	}

	private static List<String> keys(List<TagProbability> tags) {
		return tags.stream().map(TagProbability::key).toList();
	}

}
