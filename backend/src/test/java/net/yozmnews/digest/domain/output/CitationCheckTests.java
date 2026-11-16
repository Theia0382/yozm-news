package net.yozmnews.digest.domain.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.candidate.SelectionReason;
import org.junit.jupiter.api.Test;

/**
 * 인용 검증 — 모르는 별칭 · 주요 뉴스 안 중복 · 주요 뉴스끼리 중복 · 추가 헤드라인의 중복 · 빈 항목 · 인용 없음. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R6
 * <p>
 * 후보는 기사 11 · 12 · 13 · 14(별칭 A1 ~ A4)이고, 기대값은 손으로 쓴 리터럴이다.
 */
class CitationCheckTests {

	private static final DigestCandidate C11 = new DigestCandidate(new ArticleId(11), SelectionReason.SUBSCRIBED);

	private static final DigestCandidate C12 = new DigestCandidate(new ArticleId(12), SelectionReason.SUBSCRIBED);

	private static final DigestCandidate C13 = new DigestCandidate(new ArticleId(13), SelectionReason.MAJOR_STORYLINE);

	private static final DigestCandidate C14 = new DigestCandidate(new ArticleId(14), SelectionReason.SUBSCRIBED);

	private static final CandidateSet CANDIDATES = CandidateSet.of(List.of(C11, C12, C13, C14));

	@Test
	void keepsAnOutputThatCitesOnlyCandidates() {
		DigestDraft draft = new DigestDraft("흐름", List.of(new StoryDraft("t", "s", List.of("A1", "A2"))),
				List.of(new HeadlineDraft("A3", "한 줄")));

		assertThat(CitationCheck.check(draft, CANDIDATES)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory("t", "s", List.of(C11, C12))),
						List.of(new CitedHeadline(C13, "한 줄"))),
				new CitationDrops(0, 0)));
	}

	@Test
	void removesOnlyTheUnknownAlias() {
		DigestDraft draft = new DigestDraft("흐름", List.of(new StoryDraft("t", "s", List.of("A1", "Z9", "a2"))),
				List.of());

		assertThat(CitationCheck.check(draft, CANDIDATES)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory("t", "s", List.of(C11))), List.of()),
				new CitationDrops(2, 0)));
	}

	@Test
	void keepsTheFirstOfARepeatedAliasInAStory() {
		DigestDraft draft = new DigestDraft("흐름", List.of(new StoryDraft("t", "s", List.of("A2", "A1", "A2"))),
				List.of());

		assertThat(CitationCheck.check(draft, CANDIDATES)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory("t", "s", List.of(C12, C11))), List.of()),
				new CitationDrops(1, 0)));
	}

	@Test
	void dropsAStoryLeftWithoutRefs() {
		DigestDraft draft = new DigestDraft("흐름",
				List.of(new StoryDraft("빈", "s", List.of("Z1")), new StoryDraft("남음", "s", List.of("A4"))), List.of());

		assertThat(CitationCheck.check(draft, CANDIDATES)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory("남음", "s", List.of(C14))), List.of()),
				new CitationDrops(1, 1)));
	}

	@Test
	void letsStoriesShareAnArticle() {
		DigestDraft draft = new DigestDraft("흐름",
				List.of(new StoryDraft("하나", "s", List.of("A1")), new StoryDraft("둘", "s", List.of("A1", "A2"))),
				List.of());

		assertThat(CitationCheck.check(draft, CANDIDATES)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory("하나", "s", List.of(C11)),
						new CitedStory("둘", "s", List.of(C11, C12))), List.of()),
				new CitationDrops(0, 0)));
	}

	@Test
	void dropsAHeadlineAlreadyCitedByAStoryOrAnEarlierHeadline() {
		DigestDraft draft = new DigestDraft("흐름", List.of(new StoryDraft("t", "s", List.of("A1"))),
				List.of(new HeadlineDraft("A1", null), new HeadlineDraft("A2", "앞"), new HeadlineDraft("A2", "뒤"),
						new HeadlineDraft("A3", null)));

		assertThat(CitationCheck.check(draft, CANDIDATES)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory("t", "s", List.of(C11))),
						List.of(new CitedHeadline(C12, "앞"), new CitedHeadline(C13, null))),
				new CitationDrops(0, 2)));
	}

	@Test
	void dropsAHeadlineWithAnUnknownAlias() {
		DigestDraft draft = new DigestDraft("흐름", List.of(),
				List.of(new HeadlineDraft("Z9", null), new HeadlineDraft("A4", null)));

		assertThat(CitationCheck.check(draft, CANDIDATES)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(), List.of(new CitedHeadline(C14, null))), new CitationDrops(0, 1)));
	}

	@Test
	void findsNoCitationWhenEverythingIsDropped() {
		DigestDraft draft = new DigestDraft("흐름", List.of(new StoryDraft("t", "s", List.of("Z1", "Z2"))),
				List.of(new HeadlineDraft("Z3", null)));

		assertThat(CitationCheck.check(draft, CANDIDATES))
			.isEqualTo(new CitationResult.NoCitation(new CitationDrops(2, 2)));
		assertThat(CitationCheck.check(new DigestDraft("흐름", List.of(), List.of()), CANDIDATES))
			.isEqualTo(new CitationResult.NoCitation(new CitationDrops(0, 0)));
	}

	@Test
	void treatsARetainedOutCandidateAsUnknown() {
		CandidateSet present = CANDIDATES.retain(Set.of(new ArticleId(11), new ArticleId(13)));
		DigestDraft draft = new DigestDraft("흐름", List.of(new StoryDraft("t", "s", List.of("A1", "A2"))),
				List.of(new HeadlineDraft("A2", null), new HeadlineDraft("A3", null)));

		assertThat(CitationCheck.check(draft, present)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory("t", "s", List.of(C11))),
						List.of(new CitedHeadline(C13, null))),
				new CitationDrops(1, 1)));
	}

	@Test
	void treatsAnEmptyAliasAsUnknown() {
		DigestDraft draft = new DigestDraft("흐름", List.of(new StoryDraft("t", "s", List.of("A1", ""))),
				List.of(new HeadlineDraft("", null), new HeadlineDraft("A2", null)));

		assertThat(CitationCheck.check(draft, CANDIDATES)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory("t", "s", List.of(C11))),
						List.of(new CitedHeadline(C12, null))),
				new CitationDrops(1, 1)));
	}

}
