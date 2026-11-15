package net.yozmnews.credential.application;

import java.util.Objects;
import java.util.Optional;

import net.yozmnews.credential.domain.crypto.EnvelopeCipher;
import net.yozmnews.credential.domain.key.LlmKeyRepository;
import net.yozmnews.credential.domain.key.StoredLlmKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재암호화 유스케이스 — DEK 를 감싼 KEK 가 활성 버전이 아닌 키를 활성 버전으로 다시 감싼다. docs/specs/credential.md §4 ·
 * credential-R7
 * <p>
 * 키마다 트랜잭션 하나이고 그 행을 잠근 뒤 한다 — 그 사이 지워진 키와 이미 활성 버전인 키는 건너뛴다. 한 키의 실패는 키 ID 와 예외 클래스
 * 이름만 남기고 다음 키로 간다.
 */
@Service
public class DekRewrap {

	/** 한 회차에 다시 감싸는 키의 최대 수 */
	static final int BATCH = 100;

	private static final Logger logger = LoggerFactory.getLogger(DekRewrap.class);

	private final LlmKeyRepository keys;

	private final EnvelopeCipher cipher;

	private final TransactionTemplate transaction;

	DekRewrap(LlmKeyRepository keys, EnvelopeCipher cipher, TransactionTemplate transaction) {
		this.keys = Objects.requireNonNull(keys, "keys");
		this.cipher = Objects.requireNonNull(cipher, "cipher");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
	}

	/** 한 회차를 돈다 — 다시 감쌌거나 남은 키가 있을 때만 수를 로그에 남긴다 */
	public RewrapResult run() {
		int active = this.cipher.activeVersion();
		int rewrapped = 0;
		int failed = 0;
		for (long id : this.keys.idsNotOnVersion(active, BATCH)) {
			try {
				if (Boolean.TRUE.equals(this.transaction.execute(status -> rewrap(id, active)))) {
					rewrapped++;
				}
			}
			catch (RuntimeException ex) {
				failed++;
				logger.warn("KEK 재암호화 — 키 {} 를 다시 감싸지 못했다: {}", id, ex.getClass().getSimpleName());
			}
		}
		RewrapResult result = new RewrapResult(rewrapped, failed, this.keys.countNotOnVersion(active));
		if (result.rewrapped() > 0 || result.remaining() > 0) {
			logger.info("KEK 재암호화 — 다시 감쌈 {} · 실패 {} · 남음 {}", result.rewrapped(), result.failed(),
					result.remaining());
		}
		return result;
	}

	private boolean rewrap(long id, int active) {
		Optional<StoredLlmKey> locked = this.keys.lockById(id);
		if (locked.isEmpty() || locked.get().kekVersion() == active) {
			return false;
		}
		StoredLlmKey key = locked.get();
		this.keys.save(key.rewrapped(this.cipher.rewrap(key.sealed(), key.owner())));
		return true;
	}

}
