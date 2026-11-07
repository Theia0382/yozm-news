package net.yozmnews.evaluation.application;

import java.util.List;
import java.util.Objects;

import net.yozmnews.evaluation.EvaluationPeriod;
import net.yozmnews.evaluation.EventJudgementCursor;
import net.yozmnews.evaluation.EventJudgementLog;
import net.yozmnews.evaluation.EventJudgementPage;
import net.yozmnews.evaluation.EventJudgementView;
import net.yozmnews.evaluation.domain.membership.EventJudgementRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * 공개 API {@link EventJudgementLog} 의 구현 — docs/specs/evaluation.md §4 · evaluation-R39.
 * <p>
 * 한 건 더 읽어 다음 쪽이 있는지 안다. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통) — 질의 하나다.
 */
@Service
class EventJudgementLogService implements EventJudgementLog {

	private final EventJudgementRepository judgements;

	EventJudgementLogService(EventJudgementRepository judgements) {
		this.judgements = judgements;
	}

	@Override
	public EventJudgementPage list(EvaluationPeriod period, @Nullable EventJudgementCursor after, int size) {
		Objects.requireNonNull(period, "period");
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("판정 원본 목록은 한 쪽에 1 ~ " + MAX_PAGE_SIZE + "건이다: " + size);
		}
		List<EventJudgementView> rows = this.judgements.page(period.from(), period.to(),
				(after == null) ? null : after.judgedAt(), (after == null) ? null : after.id().value(), size + 1);
		List<EventJudgementView> page = (rows.size() > size) ? rows.subList(0, size) : rows;
		EventJudgementCursor next = null;
		if (rows.size() > size) {
			EventJudgementView last = page.getLast();
			next = new EventJudgementCursor(last.judgedAt(), last.id());
		}
		return new EventJudgementPage(page, next);
	}

}
