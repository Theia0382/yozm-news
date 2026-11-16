package net.yozmnews.digest.domain.output;

import java.util.Objects;

/**
 * 인용 검증의 결과 · docs/specs/digest.md §5 · digest-R6
 */
public sealed interface CitationResult {

	/** 지운 것의 수 */
	CitationDrops drops();

	/** 인용이 남았다 */
	record Cited(CitedDigest digest, CitationDrops drops) implements CitationResult {

		public Cited {
			Objects.requireNonNull(digest, "digest");
			Objects.requireNonNull(drops, "drops");
		}

	}

	/** 인용이 하나도 남지 않았다 */
	record NoCitation(CitationDrops drops) implements CitationResult {

		public NoCitation {
			Objects.requireNonNull(drops, "drops");
		}

	}

}
