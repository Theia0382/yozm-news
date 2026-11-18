package net.yozmnews.digest.adapter.out.persistence;

/**
 * 기간을 덮는 다이제스트 상태의 조건 — 정본. docs/specs/digest.md §5 · digest-R30
 * <p>
 * 다음 기간은 {@code READY} · {@code EMPTY} 로 끝난 행의 기간 끝에서 시작한다 — 실패한 기간은 다음 다이제스트가 덮는다. 기간 시작과 해결되지
 * 않은 마지막 실패(digest-R40)의 질의가 이 조각을 쓴다.
 */
final class CoveringDigestSql {

	/** {@code digests} 행의 상태가 기간을 덮는 상태인가 — 별칭 없이 쓴다 */
	static final String STATUS = "status in ('READY', 'EMPTY')";

	private CoveringDigestSql() {
	}

}
