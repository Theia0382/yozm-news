package net.yozmnews.user.domain.summary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

/**
 * 정리의 처분 — 사용자 스토리라인마다 새로 만들지 · 처음부터 다시 만들지 · 새 사건으로 고칠지 · 그대로 둘지 · 지울지를 정하는 정본.
 * docs/specs/user.md §5
 * <p>
 * 새로 만드는 것은 관심 흐름뿐이고, 이미 정리한 흐름은 관심 흐름이거나 남길 흐름인 동안 남는다. 재료가 그대로면 부르지 않는다. 연속 실패로 쉬는
 * 흐름은 부르지 않는다.
 */
public final class SummaryPlan {

	private SummaryPlan() {
	}

	/**
	 * 사용자 스토리라인 하나와 그 정리의 지금 상태.
	 *
	 * @param id 사용자 스토리라인 ID
	 * @param head 헤드 — 보일 흐름 · 남길 흐름의 판정은 헤드로 한다(헤드는 이 사용자 스토리라인에만 든다)
	 * @param tops 저장한 추적 대상(스토리라인 ID)
	 * @param tracked 추적 중인가
	 * @param summary 정리 — 없으면 null
	 * @param attempt 시도 기록 — 없으면 null
	 */
	public record Flow(long id, long head, Set<Long> tops, boolean tracked, @Nullable StorylineSummary summary,
			@Nullable SummaryAttempt attempt) {

		public Flow {
			tops = Set.copyOf(Objects.requireNonNull(tops, "tops"));
			if (!tops.contains(head)) {
				throw new IllegalArgumentException("헤드는 추적 대상에 든다: " + head + " · " + tops);
			}
		}

	}

	/** 사용자 스토리라인 하나의 처분 */
	public sealed interface Action {

		long id();

		/**
		 * 정리가 없는 관심 흐름을 처음 정리한다.
		 *
		 * @param tops 정리할 추적 대상
		 * @param send 호출에 넣을 사건 — 첫 기사 시각 순. 사건이 있는 꼭대기는 모두 하나 이상 받는다
		 * @param covered 넣은 사건으로 기록할 사건 — 상한에 걸려 넣지 않은 것 포함
		 */
		record Create(long id, Set<Long> tops, List<Long> send, Set<Long> covered) implements Action {
		}

		/** 추적 대상이 정리 때와 달라져 처음부터 다시 정리한다 — 칸은 {@link Create} 와 같다. {@code version} 은 읽은 판 */
		record Rebuild(long id, int version, Set<Long> tops, List<Long> send, Set<Long> covered) implements Action {
		}

		/** 지난 정리를 새 사건으로 고친다 — {@code send} 는 새 사건(첫 기사 시각 순), {@code covered} 는 지금 사건 전부 */
		record Extend(long id, int version, Set<Long> tops, List<Long> send, Set<Long> covered) implements Action {
		}

		/** 정리를 그대로 둔다 — 재료가 그대로이거나, 쉬는 중이거나, 이번 실행의 호출 상한에 걸렸다 */
		record Keep(long id) implements Action {
		}

		/** 관심 흐름도 남길 흐름도 아니게 된 흐름의 정리를 지운다 */
		record Remove(long id) implements Action {
		}

	}

	/**
	 * 재료(지금 사건)를 읽을 사용자 스토리라인 — 정리가 있거나 새로 만들 수 있는 것.
	 * @param followed 관심 흐름의 꼭대기
	 * @return 사용자 스토리라인 ID
	 */
	public static Set<Long> needingMaterials(List<Flow> flows, Set<Long> followed) {
		Set<Long> needing = new HashSet<>();
		for (Flow flow : flows) {
			if (flow.summary() != null || followed.contains(flow.head())) {
				needing.add(flow.id());
			}
		}
		return Set.copyOf(needing);
	}

	/**
	 * 처분을 정한다.
	 * @param followed 관심 흐름의 꼭대기
	 * @param kept 남길 흐름의 꼭대기
	 * @param events 사용자 스토리라인 ID → 추적 대상 꼭대기 → 그 꼭대기의 지금 사건(사건 ID → 첫 기사 시각). 재료를 읽지 않은 것은 없다
	 * @param now 지금 — 쉬는 끝과 견준다
	 * @return 처분 — 호출하는 것({@link Action.Create} → {@link Action.Rebuild} → {@link Action.Extend}, 그 안에서 추적 중인 것 먼저 ·
	 * ID 순)이 앞이고 {@link SummaryRules#MAX_CALLS_PER_RUN} 개까지다. 그 뒤로 지울 것 · 그대로 둘 것이 ID 순. 정리가 없고 만들지도 않는
	 * 사용자 스토리라인은 결과에 없다
	 */
	public static List<Action> decide(List<Flow> flows, Set<Long> followed, Set<Long> kept,
			Map<Long, Map<Long, Map<Long, Instant>>> events, Instant now) {
		Objects.requireNonNull(now, "now");
		List<Flow> ordered = new ArrayList<>(flows);
		ordered.sort(Comparator.comparingLong(Flow::id));
		List<Action> calls = new ArrayList<>();
		Set<Long> trackedCalls = new HashSet<>();
		List<Action> rest = new ArrayList<>();
		for (Flow flow : ordered) {
			Action action = decide(flow, followed, kept, events.getOrDefault(flow.id(), Map.of()), now);
			if (action == null) {
				continue;
			}
			switch (action) {
				case Action.Create create -> calls.add(create);
				case Action.Rebuild rebuild -> calls.add(rebuild);
				case Action.Extend extend -> calls.add(extend);
				case Action.Keep keep -> rest.add(keep);
				case Action.Remove remove -> rest.add(remove);
			}
			if (flow.tracked()) {
				trackedCalls.add(flow.id());
			}
		}
		calls.sort(Comparator.comparingInt(SummaryPlan::rank)
			.thenComparing(action -> !trackedCalls.contains(action.id()))
			.thenComparingLong(Action::id));
		List<Action> decided = new ArrayList<>(calls.subList(0, Math.min(calls.size(), SummaryRules.MAX_CALLS_PER_RUN)));
		for (Action deferred : calls.subList(decided.size(), calls.size())) {
			// 호출 상한에 걸린 것 — 정리가 있으면 그대로 두고, 없으면 다음 회차가 만든다
			if (!(deferred instanceof Action.Create)) {
				rest.add(new Action.Keep(deferred.id()));
			}
		}
		rest.sort(Comparator.comparingLong(Action::id));
		decided.addAll(rest);
		return List.copyOf(decided);
	}

	private static int rank(Action action) {
		return switch (action) {
			case Action.Create create -> 0;
			case Action.Rebuild rebuild -> 1;
			case Action.Extend extend -> 2;
			case Action.Keep keep -> 3;
			case Action.Remove remove -> 3;
		};
	}

	private static @Nullable Action decide(Flow flow, Set<Long> followed, Set<Long> kept,
			Map<Long, Map<Long, Instant>> byTop, Instant now) {
		Map<Long, Instant> events = new HashMap<>();
		byTop.values().forEach(events::putAll);
		// 헤드로 판정한다 — 갈라지기 전 꼭대기가 관심 흐름이어도 형제 갈래는 제 헤드가 관심 흐름일 때만 보인다
		boolean showable = followed.contains(flow.head());
		StorylineSummary summary = flow.summary();
		if (summary == null) {
			if (!showable || events.isEmpty() || resting(flow.attempt(), now)) {
				return null;
			}
			return new Action.Create(flow.id(), flow.tops(), chosen(byTop, events, events.keySet()),
					Set.copyOf(events.keySet()));
		}
		if (!showable && !kept.contains(flow.head())) {
			return new Action.Remove(flow.id());
		}
		if (resting(flow.attempt(), now)) {
			return new Action.Keep(flow.id());
		}
		if (!summary.tops().keySet().equals(flow.tops())) {
			if (events.isEmpty()) {
				return new Action.Keep(flow.id());
			}
			return new Action.Rebuild(flow.id(), summary.version(), flow.tops(),
					chosen(byTop, events, events.keySet()), Set.copyOf(events.keySet()));
		}
		Set<Long> added = new HashSet<>(events.keySet());
		added.removeAll(summary.events().keySet());
		if (added.isEmpty()) {
			return new Action.Keep(flow.id());
		}
		return new Action.Extend(flow.id(), summary.version(), flow.tops(), chosen(byTop, events, added),
				Set.copyOf(events.keySet()));
	}

	private static boolean resting(@Nullable SummaryAttempt attempt, Instant now) {
		if (attempt == null || attempt.failures() == 0) {
			return false;
		}
		return now.isBefore(SummaryRules.restUntil(attempt.failures(), Objects.requireNonNull(attempt.failedAt())));
	}

	/**
	 * 호출에 넣을 사건 — 대상 사건 가운데 {@link SummaryRules#MAX_EVENTS} 개까지, 첫 기사 시각 · 사건 ID 순.
	 * <p>
	 * 대상 사건이 있는 꼭대기마다 먼저 그 꼭대기의 늦은 사건을 몫(상한 ÷ 그런 꼭대기 수, 적어도 하나)만큼 담고, 남는 자리를 전체에서 늦은
	 * 순으로 채운다 — 사건이 한 꼭대기에 몰려도 대상 사건이 있는 꼭대기는 모두 사건을 하나 이상 받는다.
	 * @param byTop 꼭대기 → 그 꼭대기의 사건
	 * @param times 사건 → 첫 기사 시각
	 * @param targets 대상 사건 — 처음부터면 전부, 고칠 때면 새 사건
	 */
	private static List<Long> chosen(Map<Long, Map<Long, Instant>> byTop, Map<Long, Instant> times, Set<Long> targets) {
		Comparator<Long> byTime = Comparator.<Long, Instant>comparing(times::get).thenComparing(Comparator.naturalOrder());
		List<List<Long>> perTop = new ArrayList<>();
		for (Long top : new TreeSet<>(byTop.keySet())) {
			List<Long> ofTop = new ArrayList<>(byTop.get(top).keySet());
			ofTop.retainAll(targets);
			if (!ofTop.isEmpty()) {
				ofTop.sort(byTime);
				perTop.add(ofTop);
			}
		}
		if (perTop.isEmpty()) {
			return List.of();
		}
		int share = Math.max(1, SummaryRules.MAX_EVENTS / perTop.size());
		Set<Long> picked = new HashSet<>();
		for (List<Long> ofTop : perTop) {
			picked.addAll(ofTop.subList(Math.max(0, ofTop.size() - share), ofTop.size()));
		}
		List<Long> latestFirst = new ArrayList<>(targets);
		latestFirst.sort(byTime.reversed());
		for (Long event : latestFirst) {
			if (picked.size() >= SummaryRules.MAX_EVENTS) {
				break;
			}
			picked.add(event);
		}
		List<Long> sorted = new ArrayList<>(picked);
		sorted.sort(byTime);
		return List.copyOf(sorted);
	}

}
