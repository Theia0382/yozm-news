package net.yozmnews.user.application;

import java.time.InstantSource;

import net.yozmnews.user.domain.signup.SignUpPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가입 정책의 처음 값 적재 — 기동 때 지금 값이 없으면 닫힘을 이력과 함께 넣는다. docs/specs/user.md §4.
 * <p>
 * 두 역할 공통이다. 두 프로세스가 동시에 떠도 행 · 이력은 하나다.
 */
@Component
class SignUpPolicySeeding implements SmartInitializingSingleton {

	private static final Logger logger = LoggerFactory.getLogger(SignUpPolicySeeding.class);

	private final SignUpPolicyRepository policy;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	SignUpPolicySeeding(SignUpPolicyRepository policy, TransactionTemplate transaction) {
		this(policy, transaction, InstantSource.system());
	}

	SignUpPolicySeeding(SignUpPolicyRepository policy, TransactionTemplate transaction, InstantSource clock) {
		this.policy = policy;
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
		Boolean seeded = this.transaction.execute(status -> this.policy.seedIfAbsent(this.clock.instant()));
		if (Boolean.TRUE.equals(seeded)) {
			logger.info("가입 정책의 처음 값을 적재했다 — 공개 가입 닫힘");
			return true;
		}
		return false;
	}

}
