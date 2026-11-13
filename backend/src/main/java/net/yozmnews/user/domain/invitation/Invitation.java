package net.yozmnews.user.domain.invitation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.user.InvitationState;
import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.secret.SecretDigest;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 초대 aggregate({@code user_invitations}) — 이메일에 묶지 않은 일회용 가입 링크. 토큰은 다이제스트로만 갖는다. docs/specs/user.md §4.
 * <p>
 * 상태는 조회 때 정한다 — 쓴 것 · 취소한 것 · 만료한 것(만료 시각 포함) · 유효한 것 순이다.
 */
@Table("user_invitations")
public final class Invitation {

	@Id
	private @Nullable Long id;

	private final byte[] tokenDigest;

	private final @Nullable String memo;

	private final Instant createdAt;

	private final Instant expiresAt;

	private final @Nullable Instant revokedAt;

	private final @Nullable Instant usedAt;

	private final @Nullable Long usedBy;

	@Version
	private @Nullable Long version;

	@PersistenceCreator
	Invitation(@Nullable Long id, byte[] tokenDigest, @Nullable String memo, Instant createdAt, Instant expiresAt,
			@Nullable Instant revokedAt, @Nullable Instant usedAt, @Nullable Long usedBy, @Nullable Long version) {
		this.id = id;
		this.tokenDigest = Objects.requireNonNull(tokenDigest, "tokenDigest");
		this.memo = memo;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
		this.revokedAt = revokedAt;
		this.usedAt = usedAt;
		this.usedBy = usedBy;
		this.version = version;
	}

	/**
	 * 새 초대 — 만료는 지금에서 {@link InvitationPolicy#VALID_FOR} 뒤.
	 * @param memo 앞뒤 공백을 지우고 상한을 확인한 메모 — 없으면 null
	 */
	public static Invitation issue(SecretDigest token, @Nullable String memo, Instant now) {
		return new Invitation(null, token.value(), memo, now, now.plus(InvitationPolicy.VALID_FOR), null, null, null,
				null);
	}

	/** 조회 때의 상태 */
	public InvitationState state(Instant now) {
		if (this.usedAt != null) {
			return InvitationState.USED;
		}
		if (this.revokedAt != null) {
			return InvitationState.REVOKED;
		}
		return now.isBefore(this.expiresAt) ? InvitationState.VALID : InvitationState.EXPIRED;
	}

	/**
	 * 가입에 쓴다.
	 * @throws IllegalStateException 유효하지 않을 때
	 */
	public Invitation use(UserId user, Instant now) {
		requireValid(now);
		return new Invitation(this.id, this.tokenDigest, this.memo, this.createdAt, this.expiresAt, null, now,
				user.value(), this.version);
	}

	/**
	 * 취소한다.
	 * @throws IllegalStateException 유효하지 않을 때
	 */
	public Invitation revoke(Instant now) {
		requireValid(now);
		return new Invitation(this.id, this.tokenDigest, this.memo, this.createdAt, this.expiresAt, now, null, null,
				this.version);
	}

	private void requireValid(Instant now) {
		InvitationState state = state(now);
		if (state != InvitationState.VALID) {
			throw new IllegalStateException("유효한 초대가 아니다: " + state);
		}
	}

	/**
	 * 초대 ID.
	 * @throws IllegalStateException 아직 저장하지 않았을 때
	 */
	public long id() {
		if (this.id == null) {
			throw new IllegalStateException("저장하지 않은 초대다");
		}
		return this.id;
	}

	public Optional<String> memo() {
		return Optional.ofNullable(this.memo);
	}

	public Instant createdAt() {
		return this.createdAt;
	}

	public Instant expiresAt() {
		return this.expiresAt;
	}

	public Optional<Instant> revokedAt() {
		return Optional.ofNullable(this.revokedAt);
	}

	public Optional<Instant> usedAt() {
		return Optional.ofNullable(this.usedAt);
	}

	/** 가입한 계정 — 쓰지 않았거나 그 계정이 지워졌으면 비어 있다 */
	public Optional<UserId> usedBy() {
		return Optional.ofNullable(this.usedBy).map(UserId::new);
	}

	@Override
	public String toString() {
		return "Invitation[id=" + this.id + ", token=숨김]";
	}

}
