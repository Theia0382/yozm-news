package net.yozmnews.storyline.application;

import java.math.BigDecimal;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.BigStorylineAdministration;
import net.yozmnews.storyline.BigStorylineCriteria;
import net.yozmnews.storyline.BigStorylineCriteriaChange;
import net.yozmnews.storyline.BigStorylineCriteriaChangeResult;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.storyline.domain.big.BigStorylineCriteriaRepository;
import net.yozmnews.platform.ChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 운영 API {@link BigStorylineAdministration} 의 구현 — docs/specs/storyline.md §4 · storyline-R20.
 * <p>
 * 변경은 지금 값 행을 잠근 한 트랜잭션이다. 지금 값 읽기와 미리보기의 판정은 {@link StorylineDirectoryService} 의 것을 쓴다. 역할 애너테이션이
 * 없다(두 역할 공통).
 */
@Service
class BigStorylineAdministrationService implements BigStorylineAdministration {

	private static final Logger logger = LoggerFactory.getLogger(BigStorylineAdministrationService.class);

	private final BigStorylineCriteriaRepository criteria;

	private final StorylineDirectoryService directory;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	BigStorylineAdministrationService(BigStorylineCriteriaRepository criteria, StorylineDirectoryService directory,
			TransactionTemplate transaction) {
		this(criteria, directory, transaction, InstantSource.system());
	}

	BigStorylineAdministrationService(BigStorylineCriteriaRepository criteria, StorylineDirectoryService directory,
			TransactionTemplate transaction, InstantSource clock) {
		this.criteria = criteria;
		this.directory = directory;
		this.transaction = transaction;
		this.clock = clock;
	}

	@Override
	public BigStorylineCriteria criteria() {
		return this.directory.currentCriteria();
	}

	@Override
	public BigStorylineCriteriaChangeResult change(BigDecimal minIntensity, ChangeActor actor) {
		Objects.requireNonNull(minIntensity, "minIntensity");
		Objects.requireNonNull(actor, "actor");
		Optional<BigStorylineCriteria> proposed = BigStorylineCriteria.of(minIntensity);
		if (proposed.isEmpty()) {
			return new BigStorylineCriteriaChangeResult.OutOfRange();
		}
		BigStorylineCriteria next = proposed.get();
		BigStorylineCriteriaChangeResult result = this.transaction.execute(status -> {
			BigStorylineCriteria previous = this.criteria.lockCurrent();
			if (previous.equals(next)) {
				return new BigStorylineCriteriaChangeResult.Unchanged(previous);
			}
			this.criteria.change(previous, next, actor, this.clock.instant());
			return new BigStorylineCriteriaChangeResult.Changed(previous, next);
		});
		if (result instanceof BigStorylineCriteriaChangeResult.Changed(BigStorylineCriteria previous, BigStorylineCriteria current)) {
			logger.info("큰 판정 기준을 바꿨다 — 보도 강도 {} → {}, {}", previous.minIntensity().toPlainString(),
					current.minIntensity().toPlainString(), actor);
		}
		return Objects.requireNonNull(result);
	}

	@Override
	public List<BigStorylineCriteriaChange> changes() {
		return this.criteria.changes();
	}

	@Override
	public List<BigStoryline> preview(BigStorylineCriteria proposed, EventPeriod period) {
		return this.directory.bigStorylines(period, proposed);
	}

}
