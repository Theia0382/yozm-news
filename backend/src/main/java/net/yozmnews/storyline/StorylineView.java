package net.yozmnews.storyline;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.event.EventId;
import org.jspecify.annotations.Nullable;

/**
 * 스토리라인 하나 · docs/specs/storyline.md §3 · storyline-R21.
 *
 * @param state 조회 시각의 상태(storyline-R16)
 * @param eventCount 닫은 사건 집합(storyline-R33 — 포함의 하위까지)의 사건 수 — 기간과 무관한 스토리라인 전체. 하위가 없으면 구성 사건
 * 수다
 * @param articleCount 닫은 사건 집합의 기사 수 합 — 기간과 무관하다. 하위가 없으면 스토리라인 표현의 기사 수(storyline-R7)다
 * @param firstArticleAt 닫은 사건 집합 기사의 가장 이른 발행 시각
 * @param lastArticleAt 닫은 사건 집합 기사의 가장 늦은 발행 시각 — 상태는 이 시각으로 정한다
 * @param representativeEvent 대표 사건(storyline-R7) — 직접 소속에서 고른다. 대표 기사는 {@code event} 의 공개 조회로 읽는다
 * @param createdAt 스토리라인을 연 시각
 * @param parent 포함의 상위(storyline-R25) — 없으면 null
 */
public record StorylineView(StorylineId id, StorylineState state, int eventCount, int articleCount,
		Instant firstArticleAt, Instant lastArticleAt, EventId representativeEvent, Instant createdAt,
		@Nullable StorylineId parent) {

	public StorylineView {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(firstArticleAt, "firstArticleAt");
		Objects.requireNonNull(lastArticleAt, "lastArticleAt");
		Objects.requireNonNull(representativeEvent, "representativeEvent");
		Objects.requireNonNull(createdAt, "createdAt");
	}

}
