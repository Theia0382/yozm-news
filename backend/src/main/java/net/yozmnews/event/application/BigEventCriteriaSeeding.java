package net.yozmnews.event.application;

import java.time.InstantSource;

import net.yozmnews.event.BigEventCriteria;
import net.yozmnews.event.domain.bigevent.BigEventCriteriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 큰 사건 기준의 처음 값 적재 — 기동 때 지금 값이 없으면 {@link BigEventCriteria#DEFAULT} 를 이력과 함께 넣는다 ·
 * docs/specs/event.md §4 · event-R21.
 * <p>
 * 두 역할 공통이다. 두 프로세스가 동시에 떠도 행 · 이력은 하나다.
 */
@Component
class BigEventCriteriaSeeding implements SmartInitializingSingleton {

	private static final Logger logger = LoggerFactory.getLogger(BigEventCriteriaSeeding.class);

	private final BigEventCriteriaRepository criteria;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	BigEventCriteriaSeeding(BigEventCriteriaRepository criteria, TransactionTemplate transaction) {
		this(criteria, transaction, InstantSource.system());
	}

	BigEventCriteriaSeeding(BigEventCriteriaRepository criteria, TransactionTemplate transaction, InstantSource clock) {
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
		BigEventCriteria initial = BigEventCriteria.DEFAULT;
		Boolean seeded = this.transaction.execute(status -> this.criteria.seedIfAbsent(initial, this.clock.instant()));
		if (Boolean.TRUE.equals(seeded)) {
			logger.info("큰 사건 기준의 처음 값을 적재했다 — 소스 {} · 중요도 {}", initial.minSources(),
					initial.minImportance().toPlainString());
			return true;
		}
		return false;
	}

}
