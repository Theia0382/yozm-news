package net.yozmnews.user.application;

import java.util.Objects;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 조회 — 로그인 상태 조회가 세션 사용자의 계정을 읽는다. docs/specs/user.md §4 · user-R20
 */
@Service
public class AccountQueries {

	private final AccountRepository accounts;

	AccountQueries(AccountRepository accounts) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
	}

	/** 쓰는 계정 — 없거나 쓰지 않으면 비어 있다 */
	@Transactional(readOnly = true)
	public Optional<Account> active(UserId userId) {
		return this.accounts.findById(userId.value()).filter(Account::active);
	}

}
