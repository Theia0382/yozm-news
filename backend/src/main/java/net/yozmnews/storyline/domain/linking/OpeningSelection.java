package net.yozmnews.storyline.domain.linking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * 나란한 흐름 위에 상위를 열 때 그 상위에 들 사건을 고르는 판정 — 정본 · docs/specs/storyline.md §4.
 * <p>
 * 두 흐름의 사건마다 잇기의 규칙을 그대로 쓴다 — 기준은 잇기 기준 하나다. 점수가 위 기준 이상이면 든다. 띠 [아래 기준, 위 기준) 안이면
 * 흐름 소속 판정에 물어 확률이 확률 기준 이상일 때 든다. 그 밖은 들지 않는다(경계 포함 — {@link LinkingCriteria#inBand} ·
 * {@link Score#reaches} · {@link Asked#reaches}). 점수는 부르는 쪽이 그 사건을 뺀 두 흐름의 표현과 견줘 매긴다.
 */
public final class OpeningSelection {

	/**
	 * 한 번의 열기에서 묻는 띠 사건 수 — 측정 전 목표. 사건 하나에 묻는 후보 수({@link LinkingRule#MAX_ASKED})와 다른 값이다. 넘는 띠
	 * 사건은 이번에 들지 않는다 — 뒤에 잇기로 들 수 있다
	 */
	public static final int MAX_ASKED_EVENTS = 20;

	/** 상위를 여는 데 필요한 사건 수 — 잇기의 짝이 두 사건으로 여는 것과 같다 */
	public static final int MIN_EVENTS = 2;

	/** 점수가 높은 순, 같으면 사건 ID 가 작은 순 */
	private static final Comparator<Scored> BY_SCORE = Comparator
		.comparingDouble((Scored scored) -> scored.score().score())
		.reversed()
		.thenComparingLong(Scored::id);

	private OpeningSelection() {
	}

	/**
	 * 물을 띠 사건 — 점수 순으로 {@link #MAX_ASKED_EVENTS} 개까지.
	 * @param events 점수를 매긴 두 흐름의 사건
	 */
	public static List<Scored> toAsk(List<Scored> events, LinkingCriteria criteria) {
		Objects.requireNonNull(criteria, "criteria");
		return events.stream()
			.filter(scored -> criteria.inBand(scored.score()))
			.sorted(BY_SCORE)
			.limit(MAX_ASKED_EVENTS)
			.toList();
	}

	/**
	 * 상위에 들 사건 — 사건 ID 순.
	 * @param events 점수를 매긴 두 흐름의 사건
	 * @param asked 띠 사건에 물은 결과 — 사건 ID → 결과. 결과가 없는 띠 사건(묻지 못했거나 상한을 넘었다)은 들지 않는다
	 */
	public static List<Passed> passed(List<Scored> events, Map<Long, Asked> asked, LinkingCriteria criteria) {
		Objects.requireNonNull(asked, "asked");
		Objects.requireNonNull(criteria, "criteria");
		List<Passed> passed = new ArrayList<>();
		for (Scored scored : events.stream().sorted(Comparator.comparingLong(Scored::id)).toList()) {
			if (scored.score().reaches(criteria.upper())) {
				passed.add(new Passed(scored, null));
			}
			else if (criteria.inBand(scored.score())) {
				Asked answer = asked.get(scored.id());
				if (answer != null && answer.reaches(criteria.probability())) {
					passed.add(new Passed(scored, answer));
				}
			}
		}
		return passed;
	}

	/**
	 * 그 사건들로 상위를 열 수 있는가 — 사건이 {@link #MIN_EVENTS} 개 이상이고 어느 흐름의 구성 사건과도 같은 집합이 아니다. 흐름 하나와
	 * 같은 집합이면 정돈이 그 상위를 곧 그 흐름과 합친다.
	 * @param passed 상위에 들 사건
	 * @param first 한 흐름의 구성 사건
	 * @param second 다른 흐름의 구성 사건
	 */
	public static boolean openable(Set<Long> passed, Set<Long> first, Set<Long> second) {
		return passed.size() >= MIN_EVENTS && !passed.equals(first) && !passed.equals(second);
	}

	/**
	 * 상위에 들 사건 하나와 그 근거.
	 *
	 * @param scored 사건과 점수
	 * @param asked 띠 안에서 확률로 들었으면 그 결과 — 위 기준 이상으로 들었으면 null
	 */
	public record Passed(Scored scored, @Nullable Asked asked) {

		/**
		 * @throws IllegalArgumentException 확률 없는 결과로 들었을 때
		 */
		public Passed {
			Objects.requireNonNull(scored, "scored");
			if (asked != null && !asked.answered()) {
				throw new IllegalArgumentException("확률 없는 결과로는 들지 않는다");
			}
		}

	}

}
