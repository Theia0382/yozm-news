package net.yozmnews.user.domain.credential;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 자체 로그인 자격 aggregate — 계정마다 하나. 계정과 사용자 ID 로만 잇는다. docs/specs/user.md §4 · user-R15 · user-R16
 * <p>
 * 버전이 비어 있으면 새 행이다 — 식별자(사용자 ID)를 만들 때 정하므로 저장소가 버전으로 새 행을 가른다. 강제 변경({@link #mustChange()})은
 * 남이 정한 초기 비밀번호인 동안 참이고, 본인이 비밀번호를 바꾸면 풀린다.
 */
@Table("user_password_credentials")
public final class PasswordCredential {

	@Id
	private final Long userId;

	private final String passwordHash;

	private final Instant passwordChangedAt;

	private final boolean mustChange;

	@Version
	private @Nullable Long version;

	@PersistenceCreator
	PasswordCredential(Long userId, String passwordHash, Instant passwordChangedAt, boolean mustChange,
			@Nullable Long version) {
		this.userId = Objects.requireNonNull(userId, "userId");
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
		this.passwordChangedAt = Objects.requireNonNull(passwordChangedAt, "passwordChangedAt");
		this.mustChange = mustChange;
		this.version = version;
	}

	/**
	 * 새 자격 — 해시는 {@code PasswordHashing} 이 만든 값.
	 * @param mustChange 남이 정한 초기 비밀번호인가 — 첫 로그인에서 바꿔야 한다
	 */
	public static PasswordCredential of(UserId userId, String passwordHash, boolean mustChange, Instant now) {
		return new PasswordCredential(userId.value(), passwordHash, now, mustChange, null);
	}

	/** 본인이 정한 새 비밀번호 — 강제 변경이 풀린다 */
	public PasswordCredential changed(String passwordHash, Instant now) {
		return new PasswordCredential(this.userId, passwordHash, now, false, this.version);
	}

	/**
	 * 강제 변경 대기 계정의 초기 비밀번호를 다시 정한다 — 강제 변경은 그대로다.
	 * @throws IllegalStateException 강제 변경 대기가 아닐 때(본인이 이미 바꾼 계정)
	 */
	public PasswordCredential resetInitial(String passwordHash, Instant now) {
		if (!this.mustChange) {
			throw new IllegalStateException("본인이 비밀번호를 바꾼 계정의 초기 비밀번호는 다시 정하지 않는다");
		}
		return new PasswordCredential(this.userId, passwordHash, now, true, this.version);
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

	/** 첫 로그인에서 비밀번호를 바꿔야 하는가 */
	public boolean mustChange() {
		return this.mustChange;
	}

	@Override
	public String toString() {
		return "PasswordCredential[userId=" + this.userId + ", passwordHash=숨김, mustChange=" + this.mustChange + "]";
	}

}
