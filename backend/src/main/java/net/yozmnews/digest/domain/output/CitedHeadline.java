package net.yozmnews.digest.domain.output;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import net.yozmnews.digest.domain.candidate.DigestCandidate;

/**
 * 인용을 검증한 추가 헤드라인
 */
public record CitedHeadline(DigestCandidate ref, @Nullable String note) {

	public CitedHeadline {
		Objects.requireNonNull(ref, "ref");
	}

}
