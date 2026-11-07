package net.yozmnews.event.domain.assignment;

import net.yozmnews.event.DecidedBy;
import org.jspecify.annotations.Nullable;

/**
 * 재판정 결과를 지금 배정에 적용하는 방식 — 정본은 {@link #of} · docs/specs/event.md §4 · event-R18.
 */
public sealed interface Reassignment {

	/** 배정을 그대로 두고 기준만 새것으로 적는다 */
	record Keep() implements Reassignment {
	}

	/** 같은 사건에 남고 점수 · 정한 것을 새로 적는다 */
	record Restamp(@Nullable Double score, DecidedBy by) implements Reassignment {
	}

	/** 저장 후보인 다른 사건으로 옮긴다 */
	record Move(long eventId, double score, DecidedBy by) implements Reassignment {
	}

	/** 새 사건을 열어 옮긴다 */
	record Split(@Nullable Double bestScore, DecidedBy by) implements Reassignment {
	}

	/**
	 * 재판정 결과를 적용 방식으로 바꾼다.
	 * <ul>
	 * <li>유지 → 그대로</li>
	 * <li>지금 사건에 붙임 → 같은 사건에서 다시 적기, 다른 사건에 붙임 → 옮기기</li>
	 * <li>새 사건 — 그 기사가 판정으로 지금 사건을 열었으면 다시 적기. 아니면(붙었던 사건) 다른 기사가 있을 때 떼어 내고, 혼자면 다시
	 * 적기</li>
	 * </ul>
	 * 연 사건인지는 배정의 표지로 안다 — 병합 뒤에는 저장 후보로 추론할 수 없다(합쳐진 사건을 연 기사의 저장 후보에 남는 사건이 있을 수 있다).
	 * @param decision 재판정({@link DecisionPhase#REJUDGE})의 결과
	 * @param currentEventId 지금 배정된 사건
	 * @param opened 그 기사가 판정으로 지금 배정의 사건을 열었다 — 배정의 표지
	 * @param currentEventArticles 지금 사건의 기사 수
	 * @throws IllegalArgumentException 결과가 묻기일 때 — 재판정은 묻지 않는다
	 */
	static Reassignment of(Decision decision, long currentEventId, boolean opened, int currentEventArticles) {
		return switch (decision) {
			case Decision.Keep keep -> new Keep();
			case Decision.Attach attach -> (attach.eventId() == currentEventId)
					? new Restamp(attach.score(), attach.by())
					: new Move(attach.eventId(), attach.score(), attach.by());
			case Decision.Open open -> (!opened && currentEventArticles > 1) ? new Split(open.bestScore(), open.by())
					: new Restamp(open.bestScore(), open.by());
			case Decision.Ask ask -> throw new IllegalArgumentException("재판정은 사건 소속 판정에 묻지 않는다: " + ask);
		};
	}

}
