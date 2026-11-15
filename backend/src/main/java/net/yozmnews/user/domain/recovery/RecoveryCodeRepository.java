package net.yozmnews.user.domain.recovery;

import java.time.Instant;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.secret.SecretDigest;

/**
 * 복구 코드의 저장 인터페이스 — 계정마다 많아야 하나. 구현은 {@code adapter/out/persistence} 의 {@code JdbcRecoveryCodes}.
 * docs/specs/user.md §4
 */
public interface RecoveryCodeRepository {

	/** 계정의 복구 코드를 이 다이제스트로 둔다 — 있던 코드는 무효가 된다 */
	void replace(UserId user, SecretDigest code, Instant issuedAt);

	/** 계정의 복구 코드 다이제스트 — 없으면 비어 있다 */
	Optional<SecretDigest> find(UserId user);

}
