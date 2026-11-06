package net.yozmnews.event.domain.assignment;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 후보 산식에 넘기는 열린 사건 하나 — 저장소가 낸다.
 *
 * @param cosine 기사 벡터와 사건 중심 벡터의 코사인 유사도
 * @param keyTerms 사건 핵심 말 — 기사 수가 많은 순 상위 {@link CandidateScoring#EVENT_KEY_TERMS} 개
 * @param firstArticleAt 사건 기사 가운데 가장 이른 발행 시각
 * @param lastArticleAt 사건 기사 가운데 가장 늦은 발행 시각
 */
public record EventSnapshot(long eventId, double cosine, List<String> keyTerms, Instant firstArticleAt,
		Instant lastArticleAt) {

	public EventSnapshot {
		keyTerms = List.copyOf(keyTerms);
		Objects.requireNonNull(firstArticleAt, "firstArticleAt");
		Objects.requireNonNull(lastArticleAt, "lastArticleAt");
	}

}
