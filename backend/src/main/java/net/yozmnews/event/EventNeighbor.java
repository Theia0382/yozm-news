package net.yozmnews.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 이웃 사건 — 기준 사건과의 코사인 유사도와 함께 · docs/specs/event.md §3 · event-R24.
 *
 * @param cosine 기준 사건 중심과의 코사인 유사도(반 정밀도로 비교한다)
 * @param articleCount 구성 기사 수
 * @param firstArticleAt 구성 기사의 가장 이른 발행 시각
 * @param lastArticleAt 구성 기사의 가장 늦은 발행 시각
 * @param keyTerms 기사 수가 많은 순(같으면 말 바이트 순) 상위 핵심 말
 */
public record EventNeighbor(EventId id, double cosine, int articleCount, Instant firstArticleAt, Instant lastArticleAt,
		List<EventKeyTerm> keyTerms) {

	public EventNeighbor {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(firstArticleAt, "firstArticleAt");
		Objects.requireNonNull(lastArticleAt, "lastArticleAt");
		keyTerms = List.copyOf(keyTerms);
	}

}
