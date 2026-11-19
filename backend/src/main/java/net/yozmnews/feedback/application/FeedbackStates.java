package net.yozmnews.feedback.application;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.feedback.domain.record.FeedbackRepository;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 화면의 현재 상태 조회 — 세션 사용자의 것. docs/specs/feedback.md §4 · feedback-R7
 */
@Service
public class FeedbackStates {

	/**
	 * 한 번에 묻는 서로 다른 기사 수의 상한 — 정본. 측정 전 목표. 화면이 다이제스트 하나의 인용 기사 전부를 한 요청으로 묻는다 — 인용은 사건마다
	 * 표본 기사(사건당 상한 5)이고 사건 수는 최대 후보 수(직접 입력 상한 150) 이하라 많아야 750 이고, 이 값이 그것을 덮는다. 값의 근거는
	 * docs/specs/feedback.md §5 · feedback-R7
	 */
	public static final int MAX_ARTICLES = 750;

	private final FeedbackRepository feedback;

	FeedbackStates(FeedbackRepository feedback) {
		this.feedback = Objects.requireNonNull(feedback, "feedback");
	}

	/**
	 * 주어진 기사 가운데 그 사용자의 현재 상태가 있는 기사와 그 값을 기사 ID 순으로 읽는다. 같은 기사가 두 번 와도 한 번이다.
	 * @throws IllegalArgumentException 서로 다른 기사가 {@link #MAX_ARTICLES} 개를 넘을 때
	 */
	@Transactional(readOnly = true)
	public Map<ArticleId, FeedbackValue> among(UserId user, Collection<ArticleId> articles) {
		Objects.requireNonNull(user, "user");
		long distinct = articles.stream().distinct().count();
		if (distinct > MAX_ARTICLES) {
			throw new IllegalArgumentException("한 번에 묻는 기사는 " + MAX_ARTICLES + "개까지다: " + distinct);
		}
		return this.feedback.currentAmong(user, articles);
	}

}
