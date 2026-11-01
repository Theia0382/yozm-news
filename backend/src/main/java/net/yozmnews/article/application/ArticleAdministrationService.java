package net.yozmnews.article.application;

import java.time.InstantSource;

import net.yozmnews.article.ArticleAdministration;
import net.yozmnews.article.EntryStatistics;
import net.yozmnews.article.domain.EntryStatisticsRepository;
import net.yozmnews.article.domain.EntryWindow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 공개 운영 API {@link ArticleAdministration} 의 구현 — docs/specs/article.md §4.
 * <p>
 * 집계는 질의 하나라 트랜잭션을 열지 않는다.
 */
@Service
class ArticleAdministrationService implements ArticleAdministration {

	private final EntryStatisticsRepository statistics;

	private final InstantSource clock;

	@Autowired
	ArticleAdministrationService(EntryStatisticsRepository statistics) {
		this(statistics, InstantSource.system());
	}

	ArticleAdministrationService(EntryStatisticsRepository statistics, InstantSource clock) {
		this.statistics = statistics;
		this.clock = clock;
	}

	@Override
	public EntryStatistics entryStatistics() {
		EntryWindow window = EntryWindow.endingAt(this.clock.instant());
		return window.statistics(this.statistics.countEntries(window));
	}

}
