package net.yozmnews.user.application;

import java.util.List;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.AccountDirectory;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.AccountStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AccountDirectory} 의 구현 — 계정 저장소만 읽는다. docs/specs/account.md §4 · account-R37
 * <p>
 * 쓰지 않는다. 역할 애너테이션이 없어 두 역할 모두에서 켜진다.
 */
@Service
class AccountDirectoryService implements AccountDirectory {

	private final AccountRepository accounts;

	AccountDirectoryService(AccountRepository accounts) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
	}

	@Override
	@Transactional(readOnly = true)
	public boolean exists(UserId user) {
		return this.accounts.existsById(Objects.requireNonNull(user, "user").value());
	}

	@Override
	@Transactional(readOnly = true)
	public boolean active(UserId user) {
		return this.accounts.findById(Objects.requireNonNull(user, "user").value()).filter(Account::active).isPresent();
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserId> activeAdmins() {
		return ids(this.accounts.idsByRoleAndStatus(AccountRole.ADMIN, AccountStatus.ACTIVE));
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserId> page(long afterUserId, int limit) {
		return ids(this.accounts.idsAfter(afterUserId, checked(limit)));
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserId> activePage(long afterUserId, int limit) {
		return ids(this.accounts.idsAfterWithStatus(afterUserId, AccountStatus.ACTIVE, checked(limit)));
	}

	private static int checked(int limit) {
		if (limit < 1 || limit > MAX_PAGE) {
			throw new IllegalArgumentException("쪽 크기는 1 ~ " + MAX_PAGE + " 이어야 한다: " + limit);
		}
		return limit;
	}

	private static List<UserId> ids(List<Long> ids) {
		return ids.stream().map(UserId::new).toList();
	}

}
