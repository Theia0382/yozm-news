package net.yozmnews.article.application;

import net.yozmnews.article.ArticleId;
import net.yozmnews.article.ArticleStored;
import net.yozmnews.article.domain.ArticleMatching;
import net.yozmnews.article.domain.ArticleRepository;
import net.yozmnews.article.domain.IncomingItem;
import net.yozmnews.article.domain.MatchDecision;
import net.yozmnews.article.domain.MatchKind;
import net.yozmnews.source.ArticleFetched;
import net.yozmnews.source.SourceDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 들어온 항목 하나의 판정 · 저장 · 기록 — docs/specs/article.md §4 · article-R7 ~ article-R11 · article-R16
 */
@Service
public class ArticleIntake {

	private static final Logger logger = LoggerFactory.getLogger(ArticleIntake.class);

	private final ArticleRepository repository;

	private final SourceDirectory sources;

	private final ApplicationEventPublisher events;

	ArticleIntake(ArticleRepository repository, SourceDirectory sources, ApplicationEventPublisher events) {
		this.repository = repository;
		this.sources = sources;
		this.events = events;
	}

	@Transactional
	public void accept(ArticleFetched event) {
		IncomingItem item = IncomingItem.from(event);
		this.repository.lockSource(item.sourceId());
		// 소스 잠금 안에서 묻는다 — 삭제 처리도 이 잠금을 잡으므로, 여기서 피드가 있어 저장한 것은 삭제 처리가 지운다(article-R16)
		if (!this.sources.feedExists(item.feedId())) {
			logger.info("지워진 피드의 항목이라 저장하지 않는다 — 소스 {}, 피드 {}", item.sourceId().value(),
					item.feedId().value());
			return;
		}
		Placement placement = switch (ArticleMatching.decide(item, this.repository.findCandidates(item))) {
			case MatchDecision.New(boolean guidReused) -> store(item, guidReused);
			case MatchDecision.Existing(ArticleId articleId, MatchKind kind) -> new Placement(articleId, kind);
		};
		this.repository.insertEntryIfAbsent(placement.articleId(), item, placement.kind());
	}

	private Placement store(IncomingItem item, boolean guidReused) {
		ArticleId stored = this.repository.insertIfAbsent(item).orElse(null);
		if (stored == null) {
			// 다른 소스가 같은 정규 URL 의 기사를 먼저 저장했다 — article-R9
			ArticleId existing = this.repository.findByUrlHash(item.canonicalUrl().hash())
				.orElseThrow(() -> new IllegalStateException("정규 URL 충돌 뒤 기사가 없다: " + item.canonicalUrl()));
			return new Placement(existing, MatchKind.URL);
		}
		this.events.publishEvent(new ArticleStored(stored));
		if (guidReused) {
			logger.info("같은 피드에 같은 guid 의 다른 제목 기사가 있어 새 기사로 저장했다 — 소스 {}, 피드 {}, guid 해시 {}, 기사 {}",
					item.sourceId().value(), item.feedId().value(), item.guidHash(), stored.value());
		}
		return new Placement(stored, MatchKind.NEW);
	}

	/** 항목이 붙은 기사와 판정 종류 */
	private record Placement(ArticleId articleId, MatchKind kind) {
	}

}
