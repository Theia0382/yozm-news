package net.yozmnews.digest.domain.output;

import java.util.Objects;

/**
 * 항목 대조의 결과 · docs/specs/digest.md §5 · digest-R6
 */
public sealed interface CitationResult {

	/** 지운 것의 수 */
	CitationDrops drops();

	/** 남은 항목이 있다 */
	record Cited(CitedDigest digest, CitationDrops drops) implements CitationResult {

		public Cited {
			Objects.requireNonNull(digest, "digest");
			Objects.requireNonNull(drops, "drops");
		}

	}

	/** 남은 항목이 하나도 없다 — 인용 없음 */
	record NoCitation(CitationDrops drops) implements CitationResult {

		public NoCitation {
			Objects.requireNonNull(drops, "drops");
		}

	}

}
