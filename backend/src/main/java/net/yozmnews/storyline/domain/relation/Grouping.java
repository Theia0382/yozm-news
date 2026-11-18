package net.yozmnews.storyline.domain.relation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.storyline.RelationOutcome;

/**
 * 같은 사안의 조각으로 판정된 두 스토리라인을 한 상위 아래 묶는 판정 — 정본 · docs/specs/storyline.md §4.
 * <p>
 * 조각은 어느 쪽도 다른 쪽 안에 들지 않는다는 답이다 — 한쪽을 다른 쪽 아래 넣지 않고 같은 상위의 하위로 둔다. 상위는 지금의 포함 고리로
 * 본다(시스템이 연 상위와 잇기가 연 스토리라인을 가리지 않는다). 그 쌍 사이의 관계는 부르는 쪽이 먼저 지운다 — 넘기는 그래프는 그 쌍
 * 사이의 관계를 뺀 것이고, 그 쌍이 앞서 맺은 고리는 담는다.
 */
public final class Grouping {

	private Grouping() {
	}

	/**
	 * 두 조각을 어떻게 묶는가.
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
			return new Action.Refer(parentOfA.get(), b);
		}
		if (parentOfB.isPresent()) {
			return new Action.Refer(parentOfB.get(), a);
		}
		return new Action.Open();
	}

	/**
	 * 조각 판정의 결말 — 물은 기록에 남긴다.
	 * @param opened {@link Action.Open} 에서 상위를 열었는가 — 다른 갈래에서는 보지 않는다
	 */
	public static RelationOutcome outcomeOf(Action action, boolean opened) {
		return switch (action) {
			case Action.Open open -> opened ? RelationOutcome.GROUPED : RelationOutcome.UNGROUPED;
			case Action.Already already -> RelationOutcome.GROUPED;
			case Action.Refer refer -> RelationOutcome.REFERRED;
			case Action.Reject reject -> switch (reject.reason()) {
				case CYCLE -> RelationOutcome.CYCLE;
				case SECOND_PARENT -> RelationOutcome.SECOND_PARENT;
			};
		};
	}

	/**
	 * 조각으로 묶였던 쌍의 판정이 조각이 아니게 됐을 때 그 쌍이 맺은 고리의 처분.
	 * <p>
	 * 새 판정이 그 둘 사이의 포함이면 하위 쪽을 가리키는 고리는 지운다 — 하위는 이제 그 쌍의 상위 쪽 아래에 든다(상위는 하나다). 남은
	 * 고리는 표지를 풀어 그 고리의 두 끝이 스스로 판정받게 한다. 고리 자체는 지우지 않는다.
	 * @param decision 그 쌍의 새 판정 — 조각이 아닌 것
	 * @param grouped 그 쌍이 맺은 고리
	 * @throws IllegalArgumentException 새 판정이 조각일 때
	 */
	public static Overturn overturn(RelationRule.Decision decision, List<Relation> grouped) {
		Objects.requireNonNull(grouped, "grouped");
		Long child = switch (decision) {
			case RelationRule.Decision.Link link ->
				(link.relation().kind() == RelationKind.CONTAINS) ? link.relation().targetId() : null;
			case RelationRule.Decision.None none -> null;
			case RelationRule.Decision.Fragments fragments ->
				throw new IllegalArgumentException("조각 판정은 뒤집힘이 아니다");
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

	/** 두 조각을 묶는 갈래 */
	public sealed interface Action {

		/** 둘 다 상위가 없다 — 두 조각의 사건을 평가해 통과한 사건으로 상위를 열고 둘을 그 하위로 둔다 */
		record Open() implements Action {
		}

		/**
		 * 한쪽만 상위가 있다 — 바로 넣지 않고 그 상위와 다른 조각의 쌍을 넘겨 묻는다.
		 * @param parent 한쪽 조각의 상위
		 * @param other 상위가 없는 조각
		 */
		record Refer(long parent, long other) implements Action {
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

	/** 묶지 않는 까닭 */
	public enum Reason {

		/** 한쪽이 다른 쪽의 조상이다 — 같은 상위의 하위가 될 수 없다 */
		CYCLE,

		/** 두 조각의 상위가 서로 다르다 */
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
