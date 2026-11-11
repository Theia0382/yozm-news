package net.yozmnews.storyline;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.event.EventId;

/**
 * 스토리라인 하나 · docs/specs/storyline.md §3 · storyline-R21.
 *
 * @param state 조회 시각의 상태(storyline-R16)
 * @param eventCount 구성 사건 수 — 기간과 무관한 스토리라인 전체
 * @param articleCount 스토리라인 표현의 기사 수(storyline-R7) — 기간과 무관하다
 * @param firstArticleAt 구성 사건 기사의 가장 이른 발행 시각
 * @param lastArticleAt 구성 사건 기사의 가장 늦은 발행 시각
 * @param representativeEvent 대표 사건(storyline-R7) — 대표 기사는 {@code event} 의 공개 조회로 읽는다
 * @param createdAt 스토리라인을 연 시각
 */
public record StorylineView(StorylineId id, StorylineState state, int eventCount, int articleCount,
		Instant firstArticleAt, Instant lastArticleAt, EventId representativeEvent, Instant createdAt) {

	public StorylineView {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(firstArticleAt, "firstArticleAt");
		Objects.requireNonNull(lastArticleAt, "lastArticleAt");
		Objects.requireNonNull(representativeEvent, "representativeEvent");
		Objects.requireNonNull(createdAt, "createdAt");
	}

}
