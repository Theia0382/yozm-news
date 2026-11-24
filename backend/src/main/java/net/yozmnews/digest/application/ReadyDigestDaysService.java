package net.yozmnews.digest.application;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.digest.ReadyDigestDays;
import net.yozmnews.digest.domain.digest.DigestLedger;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 조회 {@link ReadyDigestDays} 의 구현 — docs/specs/digest.md §4. 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class ReadyDigestDaysService implements ReadyDigestDays {

	private final DigestLedger ledger;

	ReadyDigestDaysService(DigestLedger ledger) {
		this.ledger = Objects.requireNonNull(ledger, "ledger");
	}

	@Override
	@Transactional(readOnly = true)
	public int count(UserId user, Instant from, Instant to) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		if (!to.isAfter(from)) {
			throw new IllegalArgumentException("끝이 시작보다 뒤가 아니다: " + from + " ~ " + to);
		}
		return this.ledger.readyDayCount(user, from, to);
	}

}
