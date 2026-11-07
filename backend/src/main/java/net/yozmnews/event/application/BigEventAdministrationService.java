package net.yozmnews.event.application;

import java.math.BigDecimal;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.event.BigEventAdministration;
import net.yozmnews.event.BigEventCriteria;
import net.yozmnews.event.BigEventCriteriaChange;
import net.yozmnews.event.BigEventCriteriaChangeResult;
import net.yozmnews.event.domain.bigevent.BigEventCriteriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 운영 API {@link BigEventAdministration} 의 구현 — docs/specs/event.md §4 · event-R21.
 * <p>
 * 변경은 지금 값 행을 잠근 한 트랜잭션이다. 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class BigEventAdministrationService implements BigEventAdministration {

	private static final Logger logger = LoggerFactory.getLogger(BigEventAdministrationService.class);

	private final BigEventCriteriaRepository criteria;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	BigEventAdministrationService(BigEventCriteriaRepository criteria, TransactionTemplate transaction) {
		this(criteria, transaction, InstantSource.system());
	}

	BigEventAdministrationService(BigEventCriteriaRepository criteria, TransactionTemplate transaction,
			InstantSource clock) {
		this.criteria = criteria;
		this.transaction = transaction;
		this.clock = clock;
	}

	@Override
	public BigEventCriteria criteria() {
		return this.criteria.current().orElseThrow(() -> new IllegalStateException("큰 사건 기준의 처음 값이 없다"));
	}

	@Override
	public BigEventCriteriaChangeResult change(int minSources, BigDecimal minImportance, ChangeActor actor) {
		Objects.requireNonNull(minImportance, "minImportance");
		Objects.requireNonNull(actor, "actor");
		Optional<BigEventCriteria> proposed = BigEventCriteria.of(minSources, minImportance);
		if (proposed.isEmpty()) {
			return new BigEventCriteriaChangeResult.OutOfRange();
		}
		BigEventCriteria next = proposed.get();
		BigEventCriteriaChangeResult result = this.transaction.execute(status -> {
			BigEventCriteria previous = this.criteria.lockCurrent();
			if (previous.equals(next)) {
				return new BigEventCriteriaChangeResult.Unchanged(previous);
			}
			this.criteria.change(previous, next, actor, this.clock.instant());
			return new BigEventCriteriaChangeResult.Changed(previous, next);
		});
		if (result instanceof BigEventCriteriaChangeResult.Changed(BigEventCriteria previous, BigEventCriteria current)) {
			logger.info("큰 사건 기준을 바꿨다 — 소스 {} → {}, 중요도 {} → {}, {}", previous.minSources(), current.minSources(),
					previous.minImportance().toPlainString(), current.minImportance().toPlainString(), actor);
		}
		return Objects.requireNonNull(result);
	}

	@Override
	public List<BigEventCriteriaChange> changes() {
		return this.criteria.changes();
	}

}
