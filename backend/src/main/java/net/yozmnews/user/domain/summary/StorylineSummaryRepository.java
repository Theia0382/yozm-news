package net.yozmnews.user.domain.summary;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import net.yozmnews.platform.UserId;

/**
 * 정리 저장소 — 정리({@code user_storyline_summaries}) · 꼭대기마다의 글({@code user_storyline_top_summaries}) · 넣은 사건
 * ({@code user_storyline_summary_events})과 시도 기록({@code user_storyline_summary_attempts}). docs/specs/user.md §4 · §6
 * <p>
 * 사용자 스토리라인이 지워지면 네 표의 행이 함께 지워진다. 정리를 지워도 시도 기록은 남는다.
 */
public interface StorylineSummaryRepository {

	/** 그 사용자의 정리 — 마지막으로 정리한 시각 내림차순, 같으면 사용자 스토리라인 ID 순 */
	List<StorylineSummary> load(UserId user);

	/** 그 사용자의 사용자 스토리라인 하나의 정리 — 다른 사용자의 것이거나 정리가 없으면 빈 값 */
	Optional<StorylineSummary> find(UserId user, long userStorylineId);

	/**
	 * 정리를 처음 저장한다 — 판은 0 으로 쓴다.
	 * @return 저장했는가 — 그 사용자 스토리라인의 정리가 이미 있으면 아무것도 쓰지 않고 false
	 */
	boolean create(StorylineSummary summary);

	/**
	 * 정리를 고쳐 쓴다 — 글 · 꼭대기마다의 글 · 넣은 사건 · 마지막 정리의 모델과 토큰 · 마지막 정리 시각을 통째로 바꾸고 판을 하나 올린다.
	 * 처음 정리한 시각은 그대로다.
	 * @param expectedVersion 읽었을 때의 판
	 * @return 고쳐 썼는가 — 정리가 없거나 판이 다르면 아무것도 쓰지 않고 false
	 */
	boolean replace(StorylineSummary summary, int expectedVersion);

	/** 정리를 지운다 — 꼭대기마다의 글 · 넣은 사건도 함께, 시도 기록은 남는다. 없으면 아무것도 하지 않는다 */
	void delete(long userStorylineId);

	/**
	 * 이 기사 가운데 하나라도 넣은 정리를 모두 지운다 — 지운 기사 따르기 전용(여러 사용자에 걸친다).
	 * @return 정리를 지운 사용자 스토리라인 ID — ID 순
	 */
	List<Long> deleteHolding(Collection<Long> articleIds);

	/** 그 사용자의 시도 기록 — 사용자 스토리라인 ID 순 */
	List<SummaryAttempt> attempts(UserId user);

	/** 저장한 호출을 시도 기록에 더한다 — 호출 수 · 토큰 합을 올리고 연속 실패를 0 으로 */
	void recordStored(long userStorylineId, int inputTokens, int outputTokens);

	/** 결과를 버린 호출을 시도 기록에 더한다 — 호출 수 · 토큰 합만 올리고 연속 실패는 그대로 */
	void recordDiscarded(long userStorylineId, int inputTokens, int outputTokens);

	/** 실패한 호출을 시도 기록에 더한다 — 호출 수 · 토큰 합(공급자가 알려 주지 않았으면 0)을 올리고 연속 실패를 하나 올린다 */
	void recordFailed(long userStorylineId, int inputTokens, int outputTokens, String failureCode, Instant failedAt);

}
