package net.yozmnews.storyline.domain.linking;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * 잇기 판정의 정본 — 붙이기 · 짝 · 다시 넣기 · docs/specs/storyline.md §5 · storyline-R10 ~ storyline-R12 · storyline-R36.
 * <p>
 * 붙이기와 짝은 같은 규칙이다(경계 포함, 점수가 NaN 이면 어느 기준에도 닿지 않는다):
 * <ol>
 * <li>위 기준 이상이 있으면 그 가운데 점수가 가장 높은 것(같으면 ID 가 작은 것) — 묻지 않는다</li>
 * <li>없으면 띠 [아래 기준, 위 기준) 후보를 점수 순(같으면 ID 순)으로 {@link #MAX_ASKED} 개 고른다. 그 가운데 쓸 수 있는 물은 기록이 없는
 * 것이 있으면 그것들을 묻는다</li>
 * <li>모두 기록이 있으면 확률이 확률 기준 이상인 것 가운데 확률이 가장 높은 것(같으면 점수가 높은 것, 그다음 ID 가 작은 것). 확률 없는
 * 기록은 붙일 수 없다</li>
 * </ol>
 * 아래 기준이 위 기준과 같으면 띠가 비어 1 만 남는다.
 */
public final class LinkingRule {

	/** 한 번에 묻는 띠 후보 수 — 측정 전 목표 */
	public static final int MAX_ASKED = 5;

	/** 점수가 높은 순, 같으면 ID 가 작은 순 */
	private static final Comparator<Scored> BY_SCORE = Comparator
		.comparingDouble((Scored scored) -> scored.score().score())
		.reversed()
		.thenComparingLong(Scored::id);

	private LinkingRule() {
	}

	/**
	 * 붙일 스토리라인(storyline-R10) — 고른 것이 지금 소속이면 아무것도 하지 않는다({@link Choice.None}).
	 * @param storylines 점수를 매긴 후보 스토리라인 — 이미 든 스토리라인은 판정할 사건을 뺀 표현으로 매긴 점수
	 * @param current 판정할 사건이 지금 든 스토리라인
	 * @param asked 쓸 수 있는 물은 기록 — 스토리라인 ID → 결과. 다시 물을 때가 된 기록은 넣지 않는다
	 */
	public static Choice attach(List<Scored> storylines, Set<Long> current, Map<Long, Asked> asked,
			LinkingCriteria criteria) {
		return switch (choose(storylines, asked, criteria)) {
			case Choice.Take take -> current.contains(take.chosen().id()) ? new Choice.None() : take;
			case Choice.None none -> none;
			case Choice.Ask ask -> ask;
		};
	}

	/**
	 * 짝(storyline-R11) — 소속 없는 이웃 가운데서 고른다.
	 * @param affiliated 이웃 가운데 소속이 있는 사건
	 * @param asked 쓸 수 있는 물은 기록 — 이웃 사건 ID → 결과
	 */
	public static Choice pair(List<Scored> neighbors, Set<Long> affiliated, Map<Long, Asked> asked,
			LinkingCriteria criteria) {
		return choose(neighbors.stream().filter(scored -> !affiliated.contains(scored.id())).toList(), asked,
				criteria);
	}

	/**
	 * 다시 넣을 이웃(storyline-R12) — 아래 기준 이상이고 소속이 없는 이웃, 사건 ID 오름차순. 띠 안 이웃도 판정으로 붙을 수 있어 문턱은
	 * 아래 기준이다.
	 * @param affiliated 이웃 가운데 소속이 있는 사건 — 쓰기 뒤에 읽은 것
	 * @param excluded 뺄 사건 — 이번 판정으로 소속이 는 사건
	 */
	public static List<Long> requeue(List<Scored> neighbors, Set<Long> affiliated, Set<Long> excluded,
			LinkingCriteria criteria) {
		return neighbors.stream()
			.filter(scored -> scored.score().reaches(criteria.lower()))
			.map(Scored::id)
			.filter(id -> !affiliated.contains(id) && !excluded.contains(id))
			.distinct()
			.sorted()
			.toList();
	}

	private static Choice choose(List<Scored> candidates, Map<Long, Asked> asked, LinkingCriteria criteria) {
		Optional<Scored> best = candidates.stream()
			.filter(scored -> scored.score().reaches(criteria.upper()))
			.min(BY_SCORE);
		if (best.isPresent()) {
			return new Choice.Take(best.get(), null);
		}
		List<Scored> band = candidates.stream()
			.filter(scored -> criteria.inBand(scored.score()))
			.sorted(BY_SCORE)
			.limit(MAX_ASKED)
			.toList();
		List<Scored> unasked = band.stream().filter(scored -> !asked.containsKey(scored.id())).toList();
		if (!unasked.isEmpty()) {
			return new Choice.Ask(unasked);
		}
		Comparator<Scored> byProbability = Comparator
			.comparingDouble((Scored scored) -> Objects.requireNonNull(asked.get(scored.id()).probability()))
			.reversed()
			.thenComparing(BY_SCORE);
		return band.stream()
			.filter(scored -> asked.get(scored.id()).reaches(criteria.probability()))
			.min(byProbability)
			.<Choice>map(scored -> new Choice.Take(scored, asked.get(scored.id())))
			.orElseGet(Choice.None::new);
	}

	/** 판정의 결말 */
	public sealed interface Choice {

		/**
		 * 고른 것에 붙인다(짝이면 연다).
		 * @param asked 띠 안에서 확률로 골랐으면 그 결과 — 위 기준 이상으로 골랐으면 null
		 */
		record Take(Scored chosen, @Nullable Asked asked) implements Choice {

			/**
			 * @throws IllegalArgumentException 확률 없는 결과로 골랐을 때
			 */
			public Take {
				Objects.requireNonNull(chosen, "chosen");
				if (asked != null && !asked.answered()) {
					throw new IllegalArgumentException("확률 없는 결과로는 고르지 않는다");
				}
			}

		}

		/** 붙이거나 열 곳이 없다 — 또는 고른 것이 이미 소속이다 */
		record None() implements Choice {

		}

		/**
		 * 쓸 수 있는 기록이 없는 띠 후보를 묻는다 — 아무것도 쓰지 않는다.
		 * @param band 물을 후보 — 점수 순, {@link #MAX_ASKED} 개까지
		 */
		record Ask(List<Scored> band) implements Choice {

			/**
			 * @throws IllegalArgumentException 물을 후보가 없거나 상한을 넘을 때
			 */
			public Ask {
				band = List.copyOf(band);
				if (band.isEmpty() || band.size() > MAX_ASKED) {
					throw new IllegalArgumentException("물을 후보는 1 ~ " + MAX_ASKED + "개다: " + band.size());
				}
			}

		}

	}

}
