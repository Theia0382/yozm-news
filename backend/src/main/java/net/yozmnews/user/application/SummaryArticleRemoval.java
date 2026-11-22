package net.yozmnews.user.application;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.user.domain.summary.StorylineSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지운 기사 따르기 — 지운 기사를 넣은 정리를 지운다. 정리의 글은 그 기사의 제목 · 요약문에서 나온 것이라 남기지 않는다. 다음 정리 실행이 남은
 * 사건으로 처음부터 다시 만든다. 시도 기록은 남는다. docs/specs/user.md §5
 * <p>
 * 여러 사용자의 정리를 지운다 — 백그라운드(이벤트 리스너) 전용이다. 다시 처리해도 결과가 같다.
 */
@Service
class SummaryArticleRemoval {

	/** 한 문장에 싣는 기사 ID 수 */
	static final int BATCH = 1_000;

	private static final Logger logger = LoggerFactory.getLogger(SummaryArticleRemoval.class);

	private final StorylineSummaryRepository summaries;

	SummaryArticleRemoval(StorylineSummaryRepository summaries) {
		this.summaries = Objects.requireNonNull(summaries, "summaries");
	}

	/** @return 정리를 지운 사용자 스토리라인 수 */
	@Transactional
	int apply(Collection<ArticleId> articleIds) {
		List<Long> ids = articleIds.stream().map(ArticleId::value).distinct().toList();
		int removed = 0;
		for (int from = 0; from < ids.size(); from += BATCH) {
			removed += this.summaries.deleteHolding(ids.subList(from, Math.min(from + BATCH, ids.size()))).size();
		}
		if (removed > 0) {
			logger.info("지운 기사를 넣은 사용자 스토리라인 정리 {}개를 지웠다", removed);
		}
		return removed;
	}

}
