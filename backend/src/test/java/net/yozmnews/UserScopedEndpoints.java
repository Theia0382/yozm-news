package net.yozmnews;

import java.util.Map;

import net.yozmnews.credential.LlmKeyScopeTests;
import net.yozmnews.feedback.FeedbackScopeTests;
import net.yozmnews.reader.InterestHistoryScopeTests;
import net.yozmnews.reader.StorylineScreenScopeTests;
import net.yozmnews.user.AccountScopeTests;
import net.yozmnews.user.InterestScopeTests;
import net.yozmnews.user.NoticeScopeTests;
import net.yozmnews.user.StorylineSubscriptionScopeTests;

/**
 * 사용자 데이터를 다루는 화면용 API 처리기와 그 격리 시험 — 운영자 경로 밖의 처리기는 공개 표시가 없으면 여기 한 줄로 등록한다. 키는
 * {@code "메서드 경로"}(매핑 그대로, 예 {@code "GET /api/digests/{date}"}), 값은 사용자 A 의 세션으로 B 의 데이터에 닿지 않음을 보는 시험
 * 클래스다. 등록 가드는 {@link UserScopeTests}, 규범은 docs/qa/test-strategy.md §13.
 * <p>
 * 지키는 규칙: user-R6
 */
public final class UserScopedEndpoints {

	/** 등록 — 사용자 데이터 처리기를 더하는 작업이 같은 변경에 격리 시험과 함께 한 줄을 더한다 */
	public static final Map<String, Class<?>> ENDPOINTS = Map.ofEntries(
			Map.entry("GET /api/notices", NoticeScopeTests.class),
			Map.entry("GET /api/notices/unread-count", NoticeScopeTests.class),
			Map.entry("POST /api/notices/read", NoticeScopeTests.class),
			Map.entry("PUT /api/account/password", AccountScopeTests.class),
			Map.entry("POST /api/account/recovery-code", AccountScopeTests.class),
			Map.entry("GET /api/llm-keys", LlmKeyScopeTests.class),
			Map.entry("PUT /api/llm-keys/{provider}", LlmKeyScopeTests.class),
			Map.entry("POST /api/llm-keys/{provider}/checks", LlmKeyScopeTests.class),
			Map.entry("DELETE /api/llm-keys/{provider}", LlmKeyScopeTests.class),
			Map.entry("GET /api/interests/tags", InterestScopeTests.class),
			Map.entry("PUT /api/interests/tags", InterestScopeTests.class),
			Map.entry("GET /api/interests/sources", InterestScopeTests.class),
			Map.entry("PUT /api/interests/sources", InterestScopeTests.class),
			Map.entry("GET /api/interests/settings", InterestScopeTests.class),
			Map.entry("PUT /api/interests/settings", InterestScopeTests.class),
			Map.entry("GET /api/interests/model", InterestScopeTests.class),
			Map.entry("PUT /api/interests/model", InterestScopeTests.class),
			Map.entry("PUT /api/interests/effort", InterestScopeTests.class),
			Map.entry("GET /api/interests/volume", InterestScopeTests.class),
			Map.entry("PUT /api/interests/volume", InterestScopeTests.class),
			Map.entry("GET /api/storyline-subscriptions", StorylineSubscriptionScopeTests.class),
			Map.entry("PUT /api/storyline-subscriptions/{storylineId}", StorylineSubscriptionScopeTests.class),
			Map.entry("DELETE /api/storyline-subscriptions/{storylineId}", StorylineSubscriptionScopeTests.class),
			Map.entry("GET /api/storyline-subscriptions/changes", StorylineSubscriptionScopeTests.class),
			Map.entry("GET /api/storylines", StorylineScreenScopeTests.class),
			Map.entry("GET /api/storylines/{id}", StorylineScreenScopeTests.class),
			Map.entry("GET /api/storyline-cards", StorylineScreenScopeTests.class),
			Map.entry("GET /api/interest-history", InterestHistoryScopeTests.class),
			Map.entry("PUT /api/feedback/{articleId}", FeedbackScopeTests.class),
			Map.entry("GET /api/feedback", FeedbackScopeTests.class));

	private UserScopedEndpoints() {
	}

}
