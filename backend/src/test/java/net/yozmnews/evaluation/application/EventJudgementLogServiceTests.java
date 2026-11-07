package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EvaluationPeriod;
import net.yozmnews.evaluation.EventJudgementCursor;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.evaluation.EventJudgementLog;
import net.yozmnews.evaluation.EventJudgementPage;
import net.yozmnews.evaluation.EventJudgementView;
import net.yozmnews.evaluation.JudgedEvent;
import net.yozmnews.evaluation.domain.membership.EventJudgementRepository;
import net.yozmnews.evaluation.domain.membership.MembershipRecord;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * 판정 원본 목록의 쪽 — 한 건 더 읽어 다음 쪽을 알고, 쪽 크기 범위를 거부한다.
 * <p>
 * 지키는 규칙: evaluation-R39
 */
class EventJudgementLogServiceTests {

	private static final EvaluationPeriod PERIOD = new EvaluationPeriod(Instant.parse("2026-11-01T00:00:00Z"),
			Instant.parse("2026-11-08T00:00:00Z"));

	private final Rows rows = new Rows();

	private final EventJudgementLog log = new EventJudgementLogService(this.rows);

	@Test
	void readsOneMoreToKnowTheNextPage() {
		this.rows.answer = List.of(view(9, "2026-11-05T03:00:00Z"), view(8, "2026-11-05T02:00:00Z"),
				view(7, "2026-11-05T01:00:00Z"));

		EventJudgementPage page = this.log.list(PERIOD, null, 2);

		assertThat(page.judgements()).extracting(EventJudgementView::id)
			.containsExactly(new EventJudgementId(9), new EventJudgementId(8));
		assertThat(page.next())
			.isEqualTo(new EventJudgementCursor(Instant.parse("2026-11-05T02:00:00Z"), new EventJudgementId(8)));
		assertThat(this.rows.calls).containsExactly("2026-11-01T00:00:00Z ~ 2026-11-08T00:00:00Z after null/null limit 3");
	}

	@Test
	void theLastPageHasNoCursorAndPassesTheCursorOn() {
		this.rows.answer = List.of(view(7, "2026-11-05T01:00:00Z"));

		EventJudgementPage page = this.log.list(PERIOD,
				new EventJudgementCursor(Instant.parse("2026-11-05T02:00:00Z"), new EventJudgementId(8)), 2);

		assertThat(page.judgements()).hasSize(1);
		assertThat(page.next()).isNull();
		assertThat(this.rows.calls).containsExactly(
				"2026-11-01T00:00:00Z ~ 2026-11-08T00:00:00Z after 2026-11-05T02:00:00Z/8 limit 3");
	}

	@Test
	void refusesAPageSizeOutsideOneToTheMaximum() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.log.list(PERIOD, null, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> this.log.list(PERIOD, null, 101));
		assertThat(this.rows.calls).isEmpty();
		this.log.list(PERIOD, null, 1);
		this.log.list(PERIOD, null, 100);
		assertThat(this.rows.calls).containsExactly("2026-11-01T00:00:00Z ~ 2026-11-08T00:00:00Z after null/null limit 2",
				"2026-11-01T00:00:00Z ~ 2026-11-08T00:00:00Z after null/null limit 101");
	}

	private static EventJudgementView view(long id, String judgedAt) {
		return new EventJudgementView(new EventJudgementId(id), new ArticleId(5),
				List.of(new JudgedEvent(12, List.of(new ArticleId(3)), 0.83)), "jev", "jev-1.14.0", "membership-1", 812,
				Instant.parse(judgedAt));
	}

	/** 정해 둔 행을 돌려주고 받은 인자를 모은다 */
	private static final class Rows implements EventJudgementRepository {

		private List<EventJudgementView> answer = List.of();

		private final List<String> calls = new ArrayList<>();

		@Override
		public EventJudgementId insert(MembershipRecord record) {
			throw new UnsupportedOperationException("쓰지 않는다");
		}

		@Override
		public List<EventJudgementView> page(Instant from, Instant to, @Nullable Instant afterJudgedAt,
				@Nullable Long afterId, int limit) {
			this.calls.add(from + " ~ " + to + " after " + afterJudgedAt + "/" + afterId + " limit " + limit);
			return this.answer;
		}

		@Override
		public void deleteByArticles(Collection<ArticleId> articleIds) {
			throw new UnsupportedOperationException("지우지 않는다");
		}

	}

}
