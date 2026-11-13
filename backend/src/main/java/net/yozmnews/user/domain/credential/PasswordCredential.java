package net.yozmnews.user.domain.credential;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.user.UserId;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 자체 로그인 자격 aggregate — 계정마다 하나. 계정과 사용자 ID 로만 잇는다. docs/specs/user.md §4 · user-R15 · user-R16
 * <p>
 * 버전이 비어 있으면 새 행이다 — 식별자(사용자 ID)를 만들 때 정하므로 저장소가 버전으로 새 행을 가른다.
 */
@Table("user_password_credentials")
public final class PasswordCredential {

	@Id
	private final Long userId;

	private final String passwordHash;

	private final Instant passwordChangedAt;

	@Version
	private @Nullable Long version;

	@PersistenceCreator
	PasswordCredential(Long userId, String passwordHash, Instant passwordChangedAt, @Nullable Long version) {
		this.userId = Objects.requireNonNull(userId, "userId");
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
		this.passwordChangedAt = Objects.requireNonNull(passwordChangedAt, "passwordChangedAt");
		this.version = version;
	}

	/** 새 자격 — 해시는 {@code PasswordHashing} 이 만든 값 */
	public static PasswordCredential of(UserId userId, String passwordHash, Instant now) {
		return new PasswordCredential(userId.value(), passwordHash, now, null);
	}

	public UserId userId() {
		return new UserId(this.userId);
	}

	public String passwordHash() {
		return this.passwordHash;
	}

	public Instant passwordChangedAt() {
		return this.passwordChangedAt;
	}

	@Override
	public String toString() {
		return "PasswordCredential[userId=" + this.userId + ", passwordHash=숨김]";
	}

}
