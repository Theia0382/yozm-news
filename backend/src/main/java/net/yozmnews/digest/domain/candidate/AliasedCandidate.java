package net.yozmnews.digest.domain.candidate;

import java.util.Objects;

/**
 * 별칭을 매긴 기사 — 별칭은 이번 생성에서만 쓰는 짧은 이름이고 후보 행의 열쇠다 · docs/specs/digest.md §5 · digest-R3
 */
public record AliasedCandidate(String alias, DigestCandidate candidate) {

	public AliasedCandidate {
		Objects.requireNonNull(alias, "alias");
		Objects.requireNonNull(candidate, "candidate");
	}

}
