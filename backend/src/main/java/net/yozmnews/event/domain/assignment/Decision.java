package net.yozmnews.event.domain.assignment;

import java.util.List;

import org.jspecify.annotations.Nullable;

/** 판정 결과 — {@link AssignmentRule#decide} */
public sealed interface Decision {

	/** 그 사건에 붙인다 */
	record Attach(long eventId, double score, DecidedBy by) implements Decision {
	}

	/**
	 * 새 사건을 연다.
	 * @param bestScore 가장 높은 후보의 점수 — 후보가 없으면 null
	 */
	record Open(@Nullable Double bestScore, DecidedBy by) implements Decision {
	}

	/**
	 * 사건 소속 판정에 묻는다 — 처음 판정에서만.
	 * @param eventIds 물을 후보 — 점수가 높은 순(같으면 사건 ID 순), {@link AssignmentRule#MAX_ASKED} 개까지
	 */
	record Ask(List<Long> eventIds) implements Decision {

		public Ask {
			eventIds = List.copyOf(eventIds);
		}

	}

	/** 지금 배정을 유지한다 — 재판정에서만 */
	record Keep() implements Decision {
	}

}
