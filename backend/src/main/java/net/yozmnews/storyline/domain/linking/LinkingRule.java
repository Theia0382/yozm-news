package net.yozmnews.storyline.domain.linking;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 잇기 판정의 정본 — 붙이기 · 짝 · 다시 넣기 · docs/specs/storyline.md §5 · storyline-R10 ~ storyline-R12.
 * <p>
 * 셋 모두 기준 이상인 것만 본다 — 점수가 NaN 이면 기준에 닿지 않는다. 경계는 포함한다.
 */
public final class LinkingRule {

	/** 점수가 높은 순, 같으면 ID 가 작은 순 */
	private static final Comparator<Scored> BY_SCORE = Comparator
		.comparingDouble((Scored scored) -> scored.score().score())
		.reversed()
		.thenComparingLong(Scored::id);

	private LinkingRule() {
	}

	/**
	 * 붙일 스토리라인(storyline-R10) — 기준 이상 가운데 점수가 가장 높은 것(같으면 ID 가 작은 것)이 지금 소속이 아니면 그것.
	 * @param storylines 점수를 매긴 후보 스토리라인 — 이미 든 스토리라인은 판정할 사건을 뺀 표현으로 매긴 점수
	 * @param current 판정할 사건이 지금 든 스토리라인
	 * @return 가장 높은 것이 이미 소속이거나 기준 이상이 없으면 빈 값
	 */
	public static Optional<Scored> attach(List<Scored> storylines, Set<Long> current, double threshold) {
		return storylines.stream()
			.filter(scored -> scored.score().reaches(threshold))
			.min(BY_SCORE)
			.filter(best -> !current.contains(best.id()));
	}

	/**
	 * 짝(storyline-R11) — 기준 이상인 이웃을 점수 순(같으면 사건 ID 순)으로 보아 소속 없는 첫 사건.
	 * @param affiliated 이웃 가운데 소속이 있는 사건
	 */
	public static Optional<Scored> pair(List<Scored> neighbors, Set<Long> affiliated, double threshold) {
		return neighbors.stream()
			.filter(scored -> scored.score().reaches(threshold))
			.sorted(BY_SCORE)
			.filter(scored -> !affiliated.contains(scored.id()))
			.findFirst();
	}

	/**
	 * 다시 넣을 이웃(storyline-R12) — 기준 이상이고 소속이 없는 이웃, 사건 ID 오름차순.
	 * @param affiliated 이웃 가운데 소속이 있는 사건 — 쓰기 뒤에 읽은 것
	 * @param excluded 뺄 사건 — 이번 판정으로 소속이 는 사건
	 */
	public static List<Long> requeue(List<Scored> neighbors, Set<Long> affiliated, Set<Long> excluded,
			double threshold) {
		return neighbors.stream()
			.filter(scored -> scored.score().reaches(threshold))
			.map(Scored::id)
			.filter(id -> !affiliated.contains(id) && !excluded.contains(id))
			.distinct()
			.sorted()
			.toList();
	}

}
