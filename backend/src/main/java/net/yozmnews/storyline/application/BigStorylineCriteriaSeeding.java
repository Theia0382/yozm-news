package net.yozmnews.storyline.application;

import java.time.InstantSource;

import net.yozmnews.storyline.BigStorylineCriteria;
import net.yozmnews.storyline.domain.big.BigStorylineCriteriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 큰 판정 기준의 처음 값 적재 — 기동 때 지금 값이 없으면 {@link BigStorylineCriteria#DEFAULT} 를 이력과 함께 넣는다 ·
 * docs/specs/storyline.md §4 · storyline-R20.
 * <p>
 * 두 역할 공통이다. 두 프로세스가 동시에 떠도 행 · 이력은 하나다.
 */
@Component
class BigStorylineCriteriaSeeding implements SmartInitializingSingleton {

	private static final Logger logger = LoggerFactory.getLogger(BigStorylineCriteriaSeeding.class);

	private final BigStorylineCriteriaRepository criteria;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	BigStorylineCriteriaSeeding(BigStorylineCriteriaRepository criteria, TransactionTemplate transaction) {
		this(criteria, transaction, InstantSource.system());
	}

	BigStorylineCriteriaSeeding(BigStorylineCriteriaRepository criteria, TransactionTemplate transaction, InstantSource clock) {
		this.criteria = criteria;
		this.transaction = transaction;
		this.clock = clock;
	}

	@Override
	public void afterSingletonsInstantiated() {
		seedIfAbsent();
	}

	/**
	 * 지금 값이 없을 때만 처음 값을 넣는다 — 한 트랜잭션. 다시 불러도 결과가 같다.
	 * @return 넣었는가
	 */
	boolean seedIfAbsent() {
		BigStorylineCriteria initial = BigStorylineCriteria.DEFAULT;
		Boolean seeded = this.transaction.execute(status -> this.criteria.seedIfAbsent(initial, this.clock.instant()));
		if (Boolean.TRUE.equals(seeded)) {
			logger.info("큰 판정 기준의 처음 값을 적재했다 — 보도 강도 {}", initial.minIntensity().toPlainString());
			return true;
		}
		return false;
	}

}
