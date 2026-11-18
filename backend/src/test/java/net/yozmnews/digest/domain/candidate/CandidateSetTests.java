package net.yozmnews.digest.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.SelectionReason;
import org.junit.jupiter.api.Test;

/**
 * 후보와 별칭 · 후보 행의 보존 기한. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R3 · digest-R11
 */
class CandidateSetTests {

	private static final DigestCandidate C7 = new DigestCandidate(new ArticleId(7), SelectionReason.SUBSCRIBED);

	private static final DigestCandidate C3 = new DigestCandidate(new ArticleId(3), SelectionReason.MAJOR_STORYLINE);

	private static final DigestCandidate C5 = new DigestCandidate(new ArticleId(5), SelectionReason.INTEREST);

	@Test
	void aliasesCandidatesInInputOrder() {
		CandidateSet set = CandidateSet.of(List.of(C7, C3, C5));

		assertThat(set.entries()).containsExactly(new AliasedCandidate("A1", C7), new AliasedCandidate("A2", C3),
				new AliasedCandidate("A3", C5));
		assertThat(set.byAlias("A2")).contains(C3);
		assertThat(set.byAlias("a2")).isEmpty();
		assertThat(set.byAlias("A4")).isEmpty();
		assertThat(set.articleIds()).containsExactly(new ArticleId(7), new ArticleId(3), new ArticleId(5));
	}

	@Test
	void rejectsTheSameArticleTwice() {
		DigestCandidate again = new DigestCandidate(new ArticleId(7), SelectionReason.MAJOR_STORYLINE);

		assertThatThrownBy(() -> CandidateSet.of(List.of(C7, again))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void keepsAliasesWhenRetaining() {
		CandidateSet kept = CandidateSet.of(List.of(C7, C3, C5)).retain(Set.of(new ArticleId(5), new ArticleId(7)));

		assertThat(kept.entries()).containsExactly(new AliasedCandidate("A1", C7), new AliasedCandidate("A3", C5));
		assertThat(kept.byAlias("A2")).isEmpty();
		assertThat(kept.size()).isEqualTo(2);
	}

	@Test
	void allowsAnEmptySet() {
		CandidateSet empty = CandidateSet.of(List.of());

		assertThat(empty.isEmpty()).isTrue();
		assertThat(empty.articleIds()).isEmpty();
	}

	@Test
	void cutsOffThirtyDaysBeforeNow() {
		assertThat(CandidateRetention.cutoff(Instant.parse("2026-11-16T00:00:00Z")))
			.isEqualTo(Instant.parse("2026-10-17T00:00:00Z"));
	}

}
