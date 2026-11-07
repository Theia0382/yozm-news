package net.yozmnews.event;

import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 사건 소속 판정 원본을 반영한 사건 묶기 기록 — 그 판정의 쓰임과 지금 결과 · docs/specs/event.md §3 · event-R22.
 */
public sealed interface JudgementUse {

	/**
	 * 배정 판정 — 이 판정을 반영해 쓴 배정. 값은 조회 시점의 것이다(재판정 · 병합 뒤 값).
	 *
	 * @param articleId 판정한 기사
	 * @param event 그 기사의 지금 사건
	 * @param decidedBy 그 배정을 지금 정한 것
	 */
	record Assignment(ArticleId articleId, EventId event, DecidedBy decidedBy) implements JudgementUse {

		public Assignment {
			Objects.requireNonNull(articleId, "articleId");
			Objects.requireNonNull(event, "event");
			Objects.requireNonNull(decidedBy, "decidedBy");
		}

	}

	/**
	 * 병합 판정 — 이 판정으로 남긴 병합 판정 기록.
	 *
	 * @param pairs 쌍마다 — (작은 ID, 큰 ID) 순, 하나 이상
	 */
	record Merge(List<MergePair> pairs) implements JudgementUse {

		/**
		 * @throws IllegalArgumentException 쌍이 없을 때
		 */
		public Merge {
			pairs = List.copyOf(pairs);
			if (pairs.isEmpty()) {
				throw new IllegalArgumentException("병합 판정 기록이 없다");
			}
		}

	}

}
