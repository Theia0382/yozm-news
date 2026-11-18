package net.yozmnews.storyline.domain.relation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.storyline.RelationOutcome;
import org.jspecify.annotations.Nullable;

/**
 * 나란한 흐름으로 판정된 두 스토리라인을 한 상위 아래 묶는 판정 — 정본 · docs/specs/storyline.md §4.
 * <p>
 * 나란한 흐름은 어느 쪽도 다른 쪽 안에 들지 않는다는 답이다 — 한쪽을 다른 쪽 아래 넣지 않고 같은 상위의 하위로 둔다. 상위는 지금의 포함 고리로
 * 본다(시스템이 연 상위와 잇기가 연 스토리라인을 가리지 않는다). 그 쌍 사이의 관계는 부르는 쪽이 먼저 지운다 — 넘기는 그래프는 그 쌍
 * 사이의 관계를 뺀 것이고, 그 쌍이 앞서 맺은 고리는 담는다.
 * <p>
 * 한쪽이 이미 다른 하위를 가진 상위 아래 있거나, 둘 다 상위가 없고 한쪽만 하위를 가진 상위면, 상위를 새로 열지 않는다 — 다른
 * 흐름이 그 상위의 하위들과도 저마다 나란한 흐름인지를 보고({@link #among}) 모두 그러면 그 상위의 하위로 둔다. 둘 다 상위가 없고 둘
 * 다 하위를 가진 상위면 어느 쪽 아래에도 두지 않고 둘 위에 상위를 연다.
 */
public final class Grouping {

	private Grouping() {
	}

	/**
	 * 두 흐름을 어떻게 묶는가.
	 * @param graph 지금 관계에서 이 쌍 사이의 관계를 뺀 것
	 */
	public static Action decide(long a, long b, RelationGraph graph) {
		Objects.requireNonNull(graph, "graph");
		if (graph.ancestors(a).contains(b) || graph.ancestors(b).contains(a)) {
			return new Action.Reject(Reason.CYCLE);
		}
		Optional<Long> parentOfA = graph.parentOf(a);
		Optional<Long> parentOfB = graph.parentOf(b);
		if (parentOfA.isPresent() && parentOfB.isPresent()) {
			return parentOfA.get().equals(parentOfB.get()) ? new Action.Already()
					: new Action.Reject(Reason.SECOND_PARENT);
		}
		if (parentOfA.isPresent()) {
			return under(parentOfA.get(), a, b, graph);
		}
		if (parentOfB.isPresent()) {
			return under(parentOfB.get(), b, a, graph);
		}
		// 둘 다 상위가 없다 — 한쪽만 하위를 가진 상위면 그 위에 상위를 열지 않고 다른 흐름을 그 하위들과 견준다
		List<Long> childrenOfA = graph.childrenOf(a);
		List<Long> childrenOfB = graph.childrenOf(b);
		if (!childrenOfA.isEmpty() && childrenOfB.isEmpty()) {
			return new Action.Among(a, b, childrenOfA);
		}
		if (childrenOfA.isEmpty() && !childrenOfB.isEmpty()) {
			return new Action.Among(b, a, childrenOfB);
		}
		return new Action.Open();
	}

	/** 한쪽(child)에만 상위가 있다 — 그 상위에 다른 하위가 있으면 다른 흐름을 그 하위들과 견주고, 없으면 상위와의 쌍을 넘겨 묻는다 */
	private static Action under(long parent, long child, long other, RelationGraph graph) {
		List<Long> siblings = graph.childrenOf(parent).stream().filter(sibling -> sibling != child).toList();
		return siblings.isEmpty() ? new Action.Refer(parent, other) : new Action.Among(parent, other, siblings);
	}

	/**
	 * 다른 흐름을 그 상위의 하위로 둘지 — 견줄 하위마다 다른 흐름과의 마지막 결말을 본다. 하나라도 나란한 흐름이 아닌 결말이면 두지
	 * 않고, 아직 물은 적이 없는 하위가 있으면 그 쌍들을 먼저 묻고, 모두 나란한 흐름이면 하위로 둔다.
	 * @param askedOfOther 다른 흐름과 물은 상대 → 마지막 결말
	 */
	public static Consensus among(Action.Among among, Map<Long, RelationOutcome> askedOfOther) {
		Objects.requireNonNull(askedOfOther, "askedOfOther");
		List<Long> unasked = new ArrayList<>();
		for (long sibling : among.siblings()) {
			RelationOutcome outcome = askedOfOther.get(sibling);
			if (outcome == null) {
				unasked.add(sibling);
			}
			else if (!parallel(outcome)) {
				return new Consensus.Stay();
			}
		}
		return unasked.isEmpty() ? new Consensus.Join() : new Consensus.Ask(unasked);
	}

	/** 그 결말이 나란한 흐름 판정에서 나온 것인가 — 순환 · 둘째 상위는 포함 · 이어짐 판정에서도 나오므로 나란한 흐름으로 세지 않는다 */
	private static boolean parallel(RelationOutcome outcome) {
		return switch (outcome) {
			case GROUPED, REFERRED, UNGROUPED -> true;
			case LINKED, NOT_RELATED, LOW_PROBABILITY, NOT_ONE_MATTER, CYCLE, SECOND_PARENT, UNMERGED, NOTHING_TO_ASK,
					REJECTED ->
				false;
		};
	}

	/**
	 * 나란한 흐름 판정의 결말 — 물은 기록에 남긴다.
	 * @param opened {@link Action.Open} 에서 상위를 열었는가 — 다른 갈래에서는 보지 않는다
	 * @param consensus {@link Action.Among} 의 {@link #among} 결과 — 다른 갈래에서는 null
	 */
	public static RelationOutcome outcomeOf(Action action, boolean opened, @Nullable Consensus consensus) {
		return switch (action) {
			case Action.Open open -> opened ? RelationOutcome.GROUPED : RelationOutcome.UNGROUPED;
			case Action.Already already -> RelationOutcome.GROUPED;
			case Action.Refer refer -> RelationOutcome.REFERRED;
			case Action.Among among -> switch (Objects.requireNonNull(consensus, "consensus")) {
				case Consensus.Join join -> RelationOutcome.GROUPED;
				case Consensus.Ask ask -> RelationOutcome.REFERRED;
				case Consensus.Stay stay -> RelationOutcome.UNGROUPED;
			};
			case Action.Reject reject -> switch (reject.reason()) {
				case CYCLE -> RelationOutcome.CYCLE;
				case SECOND_PARENT -> RelationOutcome.SECOND_PARENT;
			};
		};
	}

	/**
	 * 나란한 흐름으로 묶였던 쌍의 판정이 나란한 흐름이 아니게 됐을 때 그 쌍이 맺은 고리의 처분.
	 * <p>
	 * 새 판정이 그 둘 사이의 포함이면 하위 쪽을 가리키는 고리는 지운다 — 하위는 이제 그 쌍의 상위 쪽 아래에 든다(상위는 하나다). 남은
	 * 고리는 표지를 풀어 그 고리의 두 끝이 스스로 판정받게 한다. 고리 자체는 지우지 않는다.
	 * @param decision 그 쌍의 새 판정 — 나란한 흐름이 아닌 것
	 * @param grouped 그 쌍이 맺은 고리
	 * @throws IllegalArgumentException 새 판정이 나란한 흐름일 때
	 */
	public static Overturn overturn(RelationRule.Decision decision, List<Relation> grouped) {
		Objects.requireNonNull(grouped, "grouped");
		Long child = switch (decision) {
			case RelationRule.Decision.Link link ->
				(link.relation().kind() == RelationKind.CONTAINS) ? link.relation().targetId() : null;
			case RelationRule.Decision.None none -> null;
			// 조각이면 그 둘은 합쳐지거나 둘로 남는다 — 어느 쪽이든 이 쌍이 맺은 고리는 더는 이 쌍의 것이 아니다
			case RelationRule.Decision.Fragments fragments -> null;
			case RelationRule.Decision.Parallel parallel ->
				throw new IllegalArgumentException("나란한 흐름 판정은 뒤집힘이 아니다");
		};
		List<Relation> removed = new ArrayList<>();
		List<Relation> released = new ArrayList<>();
		for (Relation relation : grouped) {
			if (child != null && relation.kind() == RelationKind.CONTAINS && relation.targetId() == child) {
				removed.add(relation);
			}
			else {
				released.add(relation);
			}
		}
		return new Overturn(removed, released);
	}

	/** 두 흐름을 묶는 갈래 */
	public sealed interface Action {

		/**
		 * 둘 다 상위가 없고, 둘 다 하위가 없거나 둘 다 하위를 가졌다 — 두 흐름의 사건을 평가해 통과한 사건으로 상위를 열고 둘을 그 하위로
		 * 둔다
		 */
		record Open() implements Action {
		}

		/**
		 * 한쪽만 상위가 있고 그 상위에 다른 하위가 없다 — 바로 넣지 않고 그 상위와 다른 흐름의 쌍을 넘겨 묻는다.
		 * @param parent 한쪽 흐름의 상위
		 * @param other 상위가 없는 흐름
		 */
		record Refer(long parent, long other) implements Action {
		}

		/**
		 * 한쪽이 다른 하위를 가진 상위 아래 있거나 그 자신이 하위를 가진 상위다 — 다른 흐름을 그 상위의 하위들과 견주어
		 * ({@link Grouping#among}) 그 상위의 하위로 둘지 정한다. 상위를 새로 열지 않는다.
		 * @param parent 하위를 가진 상위
		 * @param other 상위가 없는 흐름
		 * @param siblings 견줄 하위들(이 쌍의 한쪽인 하위는 뺀다) — ID 순, 하나 이상
		 */
		record Among(long parent, long other, List<Long> siblings) implements Action {

			public Among {
				siblings = List.copyOf(siblings);
				if (siblings.isEmpty()) {
					throw new IllegalArgumentException("견줄 하위가 있어야 한다");
				}
			}

		}

		/** 이미 같은 상위의 하위다 */
		record Already() implements Action {
		}

		/** 묶지 않는다 */
		record Reject(Reason reason) implements Action {

			public Reject {
				Objects.requireNonNull(reason, "reason");
			}

		}

	}

	/** 다른 흐름을 그 상위의 하위로 둘지 */
	public sealed interface Consensus {

		/** 견준 하위 모두와 나란한 흐름이다 — 그 상위의 하위로 둔다 */
		record Join() implements Consensus {
		}

		/**
		 * 아직 물은 적이 없는 하위가 있다 — 그 쌍들을 넘겨 묻는다.
		 * @param siblings 물을 하위들 — ID 순, 하나 이상
		 */
		record Ask(List<Long> siblings) implements Consensus {

			public Ask {
				siblings = List.copyOf(siblings);
			}

		}

		/** 나란한 흐름이 아닌 하위가 있다 — 그 상위 아래 두지 않는다 */
		record Stay() implements Consensus {
		}

	}

	/** 묶지 않는 까닭 */
	public enum Reason {

		/** 한쪽이 다른 쪽의 조상이다 — 같은 상위의 하위가 될 수 없다 */
		CYCLE,

		/** 두 흐름의 상위가 서로 다르다 */
		SECOND_PARENT

	}

	/**
	 * 뒤집힌 쌍이 맺은 고리의 처분.
	 *
	 * @param removed 지울 고리
	 * @param released 표지를 풀어 그 두 끝을 넘겨 물을 고리
	 */
	public record Overturn(List<Relation> removed, List<Relation> released) {

		public Overturn {
			removed = List.copyOf(removed);
			released = List.copyOf(released);
		}

	}

}
