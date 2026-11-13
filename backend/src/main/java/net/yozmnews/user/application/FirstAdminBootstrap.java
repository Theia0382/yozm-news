package net.yozmnews.user.application;

import java.time.InstantSource;
import java.util.Objects;

import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.AccountStatus;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.Onboarding;
import net.yozmnews.user.domain.bootstrap.FirstAdminDecision;
import net.yozmnews.user.domain.bootstrap.FirstAdminLock;
import net.yozmnews.user.domain.bootstrap.FirstAdminValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 첫 운영자 — 기동 때 첫 운영자 설정 값과 운영자 계정 유무로 판정해 만들거나 경고하거나 기동을 멈춘다. docs/specs/user.md §4.
 * <p>
 * 두 역할 공통이다. 판정 · 생성은 advisory 잠금 안의 트랜잭션 하나라 함께 기동한 프로세스들이 줄을 서 하나만 만들고, 나중 프로세스는 운영자를
 * 보고 값이 남았다고 경고한다. 로그 · 기동 실패 문구에 비밀번호를 싣지 않는다.
 */
@Component
@EnableConfigurationProperties(FirstAdminProperties.class)
class FirstAdminBootstrap implements SmartInitializingSingleton {

	private static final Logger logger = LoggerFactory.getLogger(FirstAdminBootstrap.class);

	private final FirstAdminProperties properties;

	private final AccountProvisioning provisioning;

	private final AccountRepository accounts;

	private final FirstAdminLock lock;

	private final InstantSource clock;

	@Autowired
	FirstAdminBootstrap(FirstAdminProperties properties, AccountProvisioning provisioning, AccountRepository accounts,
			FirstAdminLock lock) {
		this(properties, provisioning, accounts, lock, InstantSource.system());
	}

	FirstAdminBootstrap(FirstAdminProperties properties, AccountProvisioning provisioning, AccountRepository accounts,
			FirstAdminLock lock, InstantSource clock) {
		this.properties = Objects.requireNonNull(properties, "properties");
		this.provisioning = Objects.requireNonNull(provisioning, "provisioning");
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.lock = Objects.requireNonNull(lock, "lock");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public void afterSingletonsInstantiated() {
		run();
	}

	/**
	 * 판정하고 따른다.
	 * @return 판정
	 * @throws IllegalStateException 기동하지 않을 때 — 문구에 변수 이름 · 이유만 싣는다
	 */
	FirstAdminDecision run() {
		FirstAdminValues values = FirstAdminValues.of(this.properties.email(), this.properties.password());
		FirstAdminDecision decision = this.provisioning.inOwnTransaction(() -> {
			this.lock.lock();
			boolean adminExists = this.accounts.existsByRoleAndStatus(AccountRole.ADMIN, AccountStatus.ACTIVE);
			boolean emailTaken = (values instanceof FirstAdminValues.Given given)
					&& this.accounts.findByEmail(given.email().value()).isPresent();
			FirstAdminDecision decided = FirstAdminDecision.decide(values, adminExists, emailTaken);
			if (decided instanceof FirstAdminDecision.Create create) {
				ProvisionResult result = this.provisioning.provision(Onboarding.FIRST_ADMIN, create.email(),
						DisplayName.ofEmail(create.email()), create.normalizedPassword(), this.clock.instant());
				if (result instanceof ProvisionResult.EmailTaken) {
					return FirstAdminDecision.emailTaken();
				}
			}
			return decided;
		}, FirstAdminDecision::emailTaken);
		switch (decision) {
			case FirstAdminDecision.Nothing nothing -> {
			}
			case FirstAdminDecision.Create create ->
				logger.info("첫 운영자 계정을 만들었다 — {} · 첫 로그인에서 비밀번호를 바꿔야 한다", create.email().value());
			case FirstAdminDecision.WarnLeftover leftover -> logger.warn(
					"첫 운영자 값이 남아 있다(운영자가 이미 있다 — 방금 다른 프로세스가 만들었을 수 있다) — {} · {} 를 지운다",
					FirstAdminValues.EMAIL_VARIABLE, FirstAdminValues.PASSWORD_VARIABLE);
			case FirstAdminDecision.WarnNoAdmin none -> logger.warn(
					"운영자 계정이 없다 — 첫 운영자의 {} · {} 를 주고 다시 띄운다", FirstAdminValues.EMAIL_VARIABLE,
					FirstAdminValues.PASSWORD_VARIABLE);
			case FirstAdminDecision.Fail fail -> throw new IllegalStateException("첫 운영자를 만들지 못해 기동하지 않는다 — "
					+ fail.reason());
		}
		return decision;
	}

}
