package net.yozmnews.credential.application;

import java.util.Objects;
import java.util.Optional;

import net.yozmnews.credential.domain.key.KeyStatus;
import net.yozmnews.credential.domain.key.LlmKeyRepository;
import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.StoredKeyValidation;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;

/**
 * 다시 검증 유스케이스 — 저장한 키를 {@code llm} 의 저장한 키의 길로 확인하고, 그 뒤 지금 행을 다시 읽어 결과와 묶는다.
 * docs/specs/credential.md §4 · credential-R12
 * <p>
 * 트랜잭션이 없다 — 공급자 호출은 트랜잭션 밖이다. 상태를 쓰는 것은 공급 구현이고, 결과의 갈래는 전이의 정본({@link KeyStatus#after})이 정한다.
 */
@Service
public class KeyRecheck {

	private final LlmClientFactory llm;

	private final LlmKeyRepository keys;

	KeyRecheck(LlmClientFactory llm, LlmKeyRepository keys) {
		this.llm = Objects.requireNonNull(llm, "llm");
		this.keys = Objects.requireNonNull(keys, "keys");
	}

	public RecheckOutcome recheck(UserId user, Provider provider) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(provider, "provider");
		return switch (this.llm.validateStored(user, provider)) {
			case StoredKeyValidation.Missing missing -> new RecheckOutcome.Missing();
			case StoredKeyValidation.Checked checked -> current(user, provider)
				.<RecheckOutcome>map(key -> outcome(checked.result(), key))
				.orElseGet(RecheckOutcome.Missing::new);
		};
	}

	private Optional<KeyView> current(UserId user, Provider provider) {
		return this.keys.findByUserIdAndProvider(user.value(), provider.key()).map(KeyView::of);
	}

	private static RecheckOutcome outcome(KeyValidationResult result, KeyView key) {
		return switch (result) {
			case KeyValidationResult.Valid valid -> new RecheckOutcome.Valid(key);
			case KeyValidationResult.Rejected rejected -> KeyStatus.after(rejected).isPresent()
					? new RecheckOutcome.Invalid(rejected.kind(), key)
					: new RecheckOutcome.Undetermined(rejected.kind(), key);
		};
	}

}
