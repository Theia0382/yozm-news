package net.yozmnews.digest.domain.view;

import java.util.Collection;
import java.util.Objects;

import net.yozmnews.digest.SectionKind;
import net.yozmnews.digest.SelectionReason;
import org.jspecify.annotations.Nullable;

/**
 * 섹션(항목) 하나의 선정 이유 — 화면의 배지 하나 — 정본 · docs/specs/digest.md §5 다이제스트 조회.
 * <p>
 * 항목 안 사건의 이유 가운데 구독 주제가 아닌 것을 고른다 — 큰 슬롯 사건이 있으면 {@link SelectionReason#MAJOR_STORYLINE}(큰 화제),
 * 아니면 관심도로 든 사건이 있으면 {@link SelectionReason#INTEREST}(관심 반영), 아니면 {@link SelectionReason#SUBSCRIBED}. 큰 슬롯 사건은
 * 언제나 그 큰 판정 행의 항목에 들므로 큰 슬롯 항목은 {@code MAJOR_STORYLINE} 이다. 기사를 인용하지 않는 섹션(개요)은 이유가 없다.
 */
public final class SectionReasons {

	private SectionReasons() {
	}

	/**
	 * @param eventReasons 그 섹션 사건들의 선정 이유 — 순서 무관
	 * @return 섹션의 이유 — 인용하지 않는 섹션이면 null. 인용 섹션에 사건이 없으면 {@code SUBSCRIBED}(구독 주제가 아닌 표시가 없다)
	 */
	public static @Nullable SelectionReason of(SectionKind kind, Collection<SelectionReason> eventReasons) {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(eventReasons, "eventReasons");
		if (!kind.cites()) {
			return null;
		}
		SelectionReason chosen = SelectionReason.SUBSCRIBED;
		for (SelectionReason reason : eventReasons) {
			if (rank(reason) > rank(chosen)) {
				chosen = reason;
			}
		}
		return chosen;
	}

	/** 구독 주제가 아닌 표시일수록 높다 — 큰 화제 > 관심 반영 > 구독 주제 */
	private static int rank(SelectionReason reason) {
		return switch (reason) {
			case MAJOR_STORYLINE -> 2;
			case INTEREST -> 1;
			case SUBSCRIBED -> 0;
		};
	}

}
