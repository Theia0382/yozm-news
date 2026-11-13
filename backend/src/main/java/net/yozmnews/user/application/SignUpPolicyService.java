package net.yozmnews.user.application;

import java.time.InstantSource;
import java.util.List;
import java.util.Objects;

import net.yozmnews.platform.ChangeActor;
import net.yozmnews.user.SignUpPolicyAdministration;
import net.yozmnews.user.SignUpPolicyChange;
import net.yozmnews.user.SignUpPolicyChangeResult;
import net.yozmnews.user.domain.signup.SignUpPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가입 정책의 운영 — {@link SignUpPolicyAdministration} 의 구현. docs/specs/user.md §4
 */
@Service
class SignUpPolicyService implements SignUpPolicyAdministration {

	private static final Logger logger = LoggerFactory.getLogger(SignUpPolicyService.class);

	private final SignUpPolicyRepository policy;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	SignUpPolicyService(SignUpPolicyRepository policy, TransactionTemplate transaction) {
		this(policy, transaction, InstantSource.system());
	}

	SignUpPolicyService(SignUpPolicyRepository policy, TransactionTemplate transaction, InstantSource clock) {
		this.policy = policy;
		this.transaction = transaction;
		this.clock = clock;
	}

	@Override
	public boolean open() {
		return this.policy.open().orElseThrow(() -> new IllegalStateException("가입 정책의 처음 값이 없다"));
	}

	@Override
	public SignUpPolicyChangeResult change(boolean open, ChangeActor actor) {
		Objects.requireNonNull(actor, "actor");
		SignUpPolicyChangeResult result = this.transaction.execute(status -> {
			boolean previous = this.policy.lockOpen();
			if (previous == open) {
				return new SignUpPolicyChangeResult.Unchanged(previous);
			}
			this.policy.change(previous, open, actor, this.clock.instant());
			return new SignUpPolicyChangeResult.Changed(previous, open);
		});
		if (result instanceof SignUpPolicyChangeResult.Changed changed) {
			logger.info("가입 정책을 바꿨다 — 공개 가입 {} → {}, {}", label(changed.previous()), label(changed.current()), actor);
		}
		return Objects.requireNonNull(result);
	}

	@Override
	public List<SignUpPolicyChange> changes() {
		return this.policy.changes();
	}

	private static String label(boolean open) {
		return open ? "열림" : "닫힘";
	}

}
