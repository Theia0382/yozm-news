package net.yozmnews.event.application;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;

import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventIntakeAdministration;
import net.yozmnews.event.IntakeFillResult;
import net.yozmnews.event.domain.intake.IntakeQueueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 공개 운영 API {@link EventIntakeAdministration} 의 구현 — docs/specs/event.md §4. 역할 애너테이션이 없다(두 역할 공통).
 * <p>
 * 기사는 {@code article} 의 공개 조회로 고르고(표 전체 읽기 — 드문 운영 연산), 배정이 없는 기사만 한 문장으로 넣는다.
 */
@Service
class EventIntakeAdministrationService implements EventIntakeAdministration {

	private final ArticleDirectory articles;

	private final IntakeQueueRepository queue;

	private final InstantSource clock;

	@Autowired
	EventIntakeAdministrationService(ArticleDirectory articles, IntakeQueueRepository queue) {
		this(articles, queue, InstantSource.system());
	}

	EventIntakeAdministrationService(ArticleDirectory articles, IntakeQueueRepository queue, InstantSource clock) {
		this.articles = articles;
		this.queue = queue;
		this.clock = clock;
	}

	@Override
	public IntakeFillResult fill(int days) {
		if (days < 1 || days > MAX_DAYS) {
			return new IntakeFillResult.DaysOutOfRange(MAX_DAYS);
		}
		Instant now = this.clock.instant();
		List<ArticleId> seen = this.articles.firstSeenSince(now.minus(Duration.ofDays(days)));
		return new IntakeFillResult.Filled(seen.size(), this.queue.enqueueUnassigned(seen, now));
	}

}
