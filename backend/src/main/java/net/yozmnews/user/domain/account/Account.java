package net.yozmnews.user.domain.account;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 계정 aggregate — docs/specs/user.md §4 · user-R13 · user-R16
 * <p>
 * 비밀번호 해시를 갖지 않는다 — 자격은 {@code PasswordCredential} 이 사용자 ID 로 잇는다.
 */
@Table("users")
public final class Account {

	@Id
	private @Nullable Long id;

	private final String email;

	private final String displayName;

	private final AccountRole role;

	private final AccountStatus status;

	private final Instant createdAt;

	private final @Nullable Instant deletedAt;

	@Version
	private @Nullable Long version;

	@PersistenceCreator
	Account(@Nullable Long id, String email, String displayName, AccountRole role, AccountStatus status,
			Instant createdAt, @Nullable Instant deletedAt, @Nullable Long version) {
		this.id = id;
		this.email = Objects.requireNonNull(email, "email");
		this.displayName = Objects.requireNonNull(displayName, "displayName");
		this.role = Objects.requireNonNull(role, "role");
		this.status = Objects.requireNonNull(status, "status");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.deletedAt = deletedAt;
		this.version = version;
	}

	/** 쓰는 상태의 새 계정 */
	public static Account create(EmailAddress email, DisplayName displayName, AccountRole role, Instant now) {
		return new Account(null, email.value(), displayName.value(), role, AccountStatus.ACTIVE, now, null, null);
	}

	/**
	 * 사용자 식별자를 돌려준다.
	 * @throws IllegalStateException 아직 저장하지 않았을 때
	 */
	public UserId id() {
		if (this.id == null) {
			throw new IllegalStateException("저장하지 않은 계정이다");
		}
		return new UserId(this.id);
	}

	/** 로그인 · 세션을 받는 상태인가(user-R7 · user-R17) */
	public boolean active() {
		return this.status == AccountStatus.ACTIVE;
	}

	public EmailAddress email() {
		return new EmailAddress(this.email);
	}

	public String displayName() {
		return this.displayName;
	}

	public AccountRole role() {
		return this.role;
	}

	public AccountStatus status() {
		return this.status;
	}

	public Instant createdAt() {
		return this.createdAt;
	}

}
