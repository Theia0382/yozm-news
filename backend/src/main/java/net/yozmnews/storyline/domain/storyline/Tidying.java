package net.yozmnews.storyline.domain.storyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 소속이 바뀐 스토리라인의 정돈 판정 — 정본이다 · docs/specs/storyline.md §4 · storyline-R13 · storyline-R17.
 * <p>
 * 구성 사건만 보고 기준값이 없다. 같은 집합인지는 사건 ID 순 목록이 같은지로 본다. 같은 집합이면 첫 구성 사건을 반드시 가지므로, 견줄
 * 스토리라인은 첫 구성 사건을 가진 것으로 충분하다.
 */
public sealed interface Tidying {

	/** 그대로 둔다 */
	record Keep() implements Tidying {
	}

	/** 구성 사건이 없다 — 지운다(storyline-R13) */
	record Remove() implements Tidying {
	}

	/** 사건 하나뿐이고 그 사건이 다른 스토리라인에 없다 — 지우고 그 사건을 잇기 대기에 다시 넣는다(풀기) */
	record Dissolve(long eventId) implements Tidying {
	}

	/**
	 * 같은 흐름이다 — merged 의 스토리라인들이 survivor 를 가리키게 한다(storyline-R18).
	 *
	 * @param merged 합쳐질 스토리라인 — ID 순, 비지 않고 survivor 를 담지 않는다
	 */
	record MergeInto(long survivor, List<Long> merged) implements Tidying {

		public MergeInto {
			merged = List.copyOf(merged);
			if (merged.isEmpty() || merged.contains(survivor)) {
				throw new IllegalArgumentException("합쳐질 스토리라인은 비지 않고 남는 스토리라인을 담지 않는다: " + survivor + " · " + merged);
			}
		}

	}

	/**
	 * 스토리라인 하나를 정돈한다.
	 *
	 * @param members 이 스토리라인의 구성 사건 — 사건 ID 순
	 * @param sharing 이 스토리라인 밖의 합쳐지지 않은 스토리라인 가운데 {@code members} 의 첫 사건을 가진 것마다 그 구성 사건(사건 ID 순)
	 */
	static Tidying decide(long storylineId, List<Long> members, Map<Long, List<Long>> sharing) {
		Objects.requireNonNull(members, "members");
		Objects.requireNonNull(sharing, "sharing");
		if (sharing.containsKey(storylineId)) {
			throw new IllegalArgumentException("견줄 스토리라인에 자기가 들었다: " + storylineId);
		}
		if (members.isEmpty()) {
			return new Remove();
		}
		if (members.size() == 1) {
			if (sharing.isEmpty()) {
				return new Dissolve(members.get(0));
			}
			// 그 사건을 가진 스토리라인 가운데 ID 가 가장 작은 것 — 같은 집합의 남는 쪽 규칙과 같은 기준
			return new MergeInto(new TreeSet<>(sharing.keySet()).first(), List.of(storylineId));
		}
		TreeSet<Long> same = new TreeSet<>();
		sharing.forEach((other, events) -> {
			if (events.equals(members)) {
				same.add(other);
			}
		});
		if (same.isEmpty()) {
			return new Keep();
		}
		same.add(storylineId);
		long survivor = same.pollFirst();
		return new MergeInto(survivor, new ArrayList<>(same));
	}

}
