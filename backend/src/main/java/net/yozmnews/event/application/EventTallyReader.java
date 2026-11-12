package net.yozmnews.event.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventTally;
import net.yozmnews.event.domain.tally.EventTallies;
import net.yozmnews.tag.ArticleEvaluations;
import org.springframework.stereotype.Component;

/**
 * 기간 집계를 읽는 협력자 — 기사들의 소스 · 중요도를 다른 모듈의 공개 조회로 한 번씩 읽고 {@link EventTallies} 로 센다 ·
 * docs/specs/event.md §4 · event-R15.
 * <p>
 * 사건 목록과 사건 묶음 집계가 함께 쓰는 집계 경로 하나다.
 */
@Component
class EventTallyReader {

	private final ArticleDirectory articles;

	private final ArticleEvaluations evaluations;

	EventTallyReader(ArticleDirectory articles, ArticleEvaluations evaluations) {
		this.articles = articles;
		this.evaluations = evaluations;
	}

	/**
	 * @param members 묶음 키 → 기간 안 기사
	 * @return 묶음 키 → 집계 — 입력이 비면 다른 모듈을 부르지 않는다
	 */
	<K> Map<K, EventTally> read(Map<K, List<ArticleId>> members) {
		if (members.isEmpty()) {
			return Map.of();
		}
		Set<ArticleId> ids = new LinkedHashSet<>();
		members.values().forEach(ids::addAll);
		return EventTallies.tally(members, this.articles.sources(ids), this.evaluations.importances(ids));
	}

}
