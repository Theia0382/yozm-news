package net.yozmnews.digest.domain.digest;

/**
 * 다이제스트 식별자
 */
public record DigestId(long value) {

	public DigestId {
		if (value < 1) {
			throw new IllegalArgumentException("다이제스트 식별자는 1 이상이어야 한다: " + value);
		}
	}

}
