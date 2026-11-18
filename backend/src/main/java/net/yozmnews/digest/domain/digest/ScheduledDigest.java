package net.yozmnews.digest.domain.digest;

import java.util.Objects;
import java.util.UUID;

/**
 * 생성 작업을 등록할 다이제스트 — 다이제스트와 그 등록 토큰(작업 ID). docs/specs/digest.md §4
 */
public record ScheduledDigest(DigestId id, UUID token) {

	public ScheduledDigest {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(token, "token");
	}

}
