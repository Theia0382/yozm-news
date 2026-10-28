package net.yozmnews.source.domain.poll;

import java.util.List;
import java.util.Objects;

import net.yozmnews.source.FetchedArticle;

/**
 * 수집 결과 하나를 반영한 결과.
 *
 * @param state 반영한 뒤의 수집 상태 — 점유가 없다
 * @param newArticles 발행할 기사 — 성공한 수집의 새 항목, 그 밖에는 빈 목록
 * @param overflowSuspected 이번 결과가 넘침 의심인가
 * @param movedToChanged 영구 이동 주소가 바뀌어 null 이 아닌 값이 되었는가 — 운영자가 프로필을 고칠 단서
 */
public record PollOutcome(FeedPollState state, List<FetchedArticle> newArticles, boolean overflowSuspected,
		boolean movedToChanged) {

	public PollOutcome {
		Objects.requireNonNull(state, "state");
		newArticles = List.copyOf(newArticles);
	}

}
