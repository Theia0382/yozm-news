package net.yozmnews.reader.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.ShownDigest;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.UserStorylineSummaryRef;

/**
 * 날짜의 보이는 다이제스트 — 기간 · 모델 · 토큰 · 섹션. docs/specs/reader.md §5 · reader-R9
 *
 * @param digestId 다이제스트 ID
 * @param periodStart 기간 시작
 * @param periodEnd 기간 끝(제외)
 * @param provider 쓴 모델의 공급자 키
 * @param model 쓴 모델 ID
 * @param candidateCount 생성에 넘긴 후보 수
 * @param inputTokens 입력 토큰
 * @param outputTokens 출력 토큰
 * @param completedAt 끝낸 시각
 * @param sections 섹션 — 위치 순. 인용한 기사가 모두 지워졌으면 비어 있다
 */
record ShownDigestResponse(long digestId, Instant periodStart, Instant periodEnd, String provider, String model,
		int candidateCount, int inputTokens, int outputTokens, Instant completedAt, List<DigestSectionResponse> sections) {

	static ShownDigestResponse of(ShownDigest shown, Map<ArticleId, NewsArticleResponse> articles,
			Map<StorylineId, List<UserStorylineSummaryRef>> userStorylines) {
		return new ShownDigestResponse(shown.digestId(), shown.periodStart(), shown.periodEnd(), shown.provider(),
				shown.model(), shown.candidateCount(), shown.inputTokens(), shown.outputTokens(), shown.completedAt(),
				shown.sections()
					.stream()
					.map(section -> DigestSectionResponse.of(section, articles, userStorylines))
					.toList());
	}

}
