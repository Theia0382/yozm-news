package net.yozmnews.reader.adapter.in.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestDay;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.UserStorylineSummaryRef;
import org.jspecify.annotations.Nullable;

/**
 * 날짜 하나의 다이제스트 — 보이는 다이제스트 · 가장 최근 시도 · 그 날짜의 사용량 · 이웃 날짜. docs/specs/reader.md §5 · reader-R9
 *
 * @param date 다이제스트 날짜
 * @param previousDate 다이제스트가 있는 바로 앞 날짜 — 없으면 null
 * @param nextDate 다이제스트가 있는 바로 뒤 날짜 — 없으면 null
 * @param shown 보이는 다이제스트 — 없으면 null
 * @param latest 가장 최근 시도 — {@code shown} 과 같은 다이제스트일 수 있다
 * @param usage 그 날짜 모든 시도의 수와 토큰 합
 */
record DigestDayResponse(LocalDate date, @Nullable LocalDate previousDate, @Nullable LocalDate nextDate,
		@Nullable ShownDigestResponse shown, DigestAttemptResponse latest, DigestUsageResponse usage) {

	/** @param articles 인용 기사의 정보 — 없는 기사의 인용은 뺀다(reader-R7) */
	static DigestDayResponse of(DigestDay day, Map<ArticleId, NewsArticleResponse> articles,
			Map<StorylineId, UserStorylineSummaryRef> userStorylines) {
		return new DigestDayResponse(day.date(), day.previousDate(), day.nextDate(),
				(day.shown() == null) ? null : ShownDigestResponse.of(day.shown(), articles, userStorylines),
				DigestAttemptResponse.of(day.latest()), DigestUsageResponse.of(day.usage()));
	}

	/** 보이는 다이제스트의 항목을 묶은 흐름 — 서로 다른 것 */
	static List<StorylineId> flows(DigestDay day) {
		if (day.shown() == null) {
			return List.of();
		}
		return day.shown().sections().stream().map(section -> section.storyline()).filter(Objects::nonNull).distinct().toList();
	}

	/** 날짜 보기가 인용한 기사 ID — 기사 정보를 한 번에 읽으려고 */
	static List<ArticleId> citedArticles(DigestDay day) {
		if (day.shown() == null) {
			return List.of();
		}
		return day.shown()
			.sections()
			.stream()
			.flatMap(section -> section.citations().stream())
			.map(citation -> citation.article())
			.toList();
	}

}
