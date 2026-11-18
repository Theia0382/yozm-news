package net.yozmnews.storyline.domain.relation;

import java.util.Objects;

/**
 * 같은 사안의 조각으로 판정된 두 스토리라인을 합칠지의 판정 — 정본 · docs/specs/storyline.md §4 · storyline-R17.
 * <p>
 * 조각 답의 확률이 합치기 확률 기준 이상이면 합친다 — 경계는 포함한다. 남는 쪽은 ID 가 작은 스토리라인이다(정돈의 합치기와 같은
 * 규칙). 합치기는 한 방향이라 관계의 확률 기준과 따로 조인다. 구조(조상 · 상위)는 보지 않는다 — 합친 뒤 관계는
 * {@link RelationMove#plan} 이 옮긴다.
 */
public final class FragmentMerge {

	private FragmentMerge() {
	}

	public static Action decide(RelationRule.Decision.Fragments fragments, RelationCriteria criteria) {
		Objects.requireNonNull(fragments, "fragments");
		Objects.requireNonNull(criteria, "criteria");
		if (fragments.probability() < criteria.mergeProbability()) {
			return new Action.Keep();
		}
		return new Action.Merge(Math.min(fragments.first(), fragments.second()),
				Math.max(fragments.first(), fragments.second()));
	}

	/** 조각 쌍의 처분 */
	public sealed interface Action {

		/**
		 * 합친다.
		 *
		 * @param survivor 남는 스토리라인 — 두 스토리라인 가운데 ID 가 작은 것
		 * @param merged 합쳐질 스토리라인
		 */
		record Merge(long survivor, long merged) implements Action {
		}

		/** 합치지 않는다 — 두 스토리라인은 둘로 남고 그 쌍의 관계는 지운다 */
		record Keep() implements Action {
		}

	}

}
