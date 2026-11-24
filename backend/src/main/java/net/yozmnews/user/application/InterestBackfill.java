package net.yozmnews.user.application;

import java.time.InstantSource;
import java.util.List;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.AccountDirectory;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기존 계정 채우기 — 설정 행이 없는 계정에 관심 설정 기본값을 넣는다 — docs/specs/user.md §4 · user-R44
 * <p>
 * 기동 단계({@link UserStartup#PHASE})에서 두 역할 공통으로 돈다 — 활성 태그 체계를 읽으므로 첫 기동 적재 뒤다. 대상은 설정 행으로만 고른다 —
 * 형식 제외 행이 없는 계정은 사용자가 모두 풀었을 수 있다. 계정을 {@code account} 의 공개 조회로 계정 ID 순 묶음씩 읽어,
 * 그 가운데 설정 행이 없는 계정마다 트랜잭션 하나로 쓴다. 동시에 뜬 프로세스 · 계정 생김
 * 리스너와 겹쳐도 설정 행을 없을 때만 넣으므로 한 번이다. 한 계정의 실패는 기동을 멈춘다.
 */
@Component
class InterestBackfill implements SmartLifecycle {

	private static final Logger logger = LoggerFactory.getLogger(InterestBackfill.class);

	/** 한 번에 읽는 계정 수 */
	static final int BATCH = 100;

	private final AccountDirectory accounts;

	private final InterestSettingsRepository settings;

	private final InterestDefaultsWriter defaults;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	private volatile boolean ran;

	InterestBackfill(AccountDirectory accounts, InterestSettingsRepository settings, InterestDefaultsWriter defaults,
			TransactionTemplate transaction, InstantSource clock) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.defaults = Objects.requireNonNull(defaults, "defaults");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public void start() {
		if (this.ran) {
			return;
		}
		fill();
		this.ran = true;
	}

	@Override
	public void stop() {
		// 기동 때 한 번 하는 일이라 멈출 것이 없다
	}

	@Override
	public boolean isRunning() {
		return this.ran;
	}

	@Override
	public int getPhase() {
		return UserStartup.PHASE;
	}

	/**
	 * 설정 행이 없는 계정을 모두 채운다.
	 * @return 채운 계정 수
	 */
	int fill() {
		int filled = 0;
		long after = 0;
		while (true) {
			List<UserId> batch = this.accounts.page(after, BATCH);
			if (batch.isEmpty()) {
				break;
			}
			for (UserId user : this.settings.withoutSettingsAmong(batch)) {
				if (fillOne(user)) {
					filled++;
				}
			}
			after = batch.getLast().value();
		}
		if (filled > 0) {
			logger.info("설정 행이 없던 계정 {}개에 관심 설정 기본값을 넣었다", filled);
		}
		return filled;
	}

	private boolean fillOne(UserId user) {
		return Boolean.TRUE.equals(this.transaction.execute(status -> this.defaults.createFor(user, this.clock.instant())));
	}

}
