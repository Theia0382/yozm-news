package net.yozmnews.user.domain.suggestion;

import java.util.List;

import net.yozmnews.platform.UserId;

/**
 * 구독 제안 거절 저장 — 구현은 {@code adapter/out/persistence} 의 {@code JdbcTopicSuggestionDeclines}. docs/specs/user.md §4 ·
 * user-R64
 */
public interface TopicSuggestionDeclineRepository {

	/** 거절을 적는다 — 같은 사용자 · 버전 · 키의 행이 있으면 관심 · 시각을 바꾼다. 한 문장이라 트랜잭션 없이 불러도 된다 */
	void upsert(UserId user, RecordedDecline decline);

	/** 그 사용자의 거절 행 전부 — 기록 당시 버전의 키 그대로 */
	List<RecordedDecline> load(UserId user);

}
