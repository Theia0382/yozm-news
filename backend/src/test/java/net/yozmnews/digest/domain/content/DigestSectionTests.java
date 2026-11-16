package net.yozmnews.digest.domain.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.candidate.SelectionReason;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.digest.domain.output.CitedDigest;
import net.yozmnews.digest.domain.output.CitedHeadline;
import net.yozmnews.digest.domain.output.CitedStory;
import org.junit.jupiter.api.Test;

/**
 * 내용 저장 꼴 — 개요 · 주요 뉴스 · 추가 헤드라인 묶음의 순서와 인용. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R7
 */
class DigestSectionTests {

	private static final DigestCandidate C11 = new DigestCandidate(new ArticleId(11), SelectionReason.SUBSCRIBED);

	private static final DigestCandidate C12 = new DigestCandidate(new ArticleId(12), SelectionReason.MAJOR_STORYLINE);

	private static final DigestCandidate C13 = new DigestCandidate(new ArticleId(13), SelectionReason.SUBSCRIBED);

	@Test
	void layoutsOverviewStoriesAndHeadlines() {
		CitedDigest cited = new CitedDigest("흐름",
				List.of(new CitedStory("하나", "요약 하나", List.of(C11, C12)), new CitedStory("둘", "요약 둘", List.of(C12))),
				List.of(new CitedHeadline(C13, "한 줄")));

		List<DigestSection> sections = DigestSection.of(new DigestId(9), cited);

		assertThat(sections).extracting(DigestSection::kind, DigestSection::position)
			.containsExactly(tuple(SectionKind.OVERVIEW, 0),
					tuple(SectionKind.TOP_STORY, 1),
					tuple(SectionKind.TOP_STORY, 2),
					tuple(SectionKind.MORE_HEADLINES, 3));
		assertThat(sections.get(0).body()).contains("흐름");
		assertThat(sections.get(0).title()).isEmpty();
		assertThat(sections.get(0).items()).isEmpty();
		assertThat(sections.get(1).title()).contains("하나");
		assertThat(sections.get(1).body()).contains("요약 하나");
		assertThat(sections.get(1).items()).containsExactly(new DigestItem(11, null, SelectionReason.SUBSCRIBED),
				new DigestItem(12, null, SelectionReason.MAJOR_STORYLINE));
		assertThat(sections.get(3).title()).isEmpty();
		assertThat(sections.get(3).body()).isEmpty();
		assertThat(sections.get(3).items()).containsExactly(new DigestItem(13, "한 줄", SelectionReason.SUBSCRIBED));
		assertThat(sections).allSatisfy(section -> assertThat(section.digestId()).isEqualTo(new DigestId(9)));
	}

	@Test
	void leavesOutTheHeadlineSectionWhenThereAreNoHeadlines() {
		CitedDigest cited = new CitedDigest("흐름", List.of(new CitedStory("하나", "s", List.of(C11))), List.of());

		assertThat(DigestSection.of(new DigestId(9), cited)).extracting(DigestSection::kind)
			.containsExactly(SectionKind.OVERVIEW, SectionKind.TOP_STORY);
	}

	@Test
	void layoutsHeadlinesRightAfterTheOverviewWhenThereAreNoStories() {
		CitedDigest cited = new CitedDigest("흐름", List.of(), List.of(new CitedHeadline(C11, null)));

		assertThat(DigestSection.of(new DigestId(9), cited)).extracting(DigestSection::kind, DigestSection::position)
			.containsExactly(tuple(SectionKind.OVERVIEW, 0),
					tuple(SectionKind.MORE_HEADLINES, 1));
	}

	@Test
	void knowsWhichKindsCite() {
		assertThat(SectionKind.OVERVIEW.cites()).isFalse();
		assertThat(SectionKind.TOP_STORY.cites()).isTrue();
		assertThat(SectionKind.MORE_HEADLINES.cites()).isTrue();
	}

}
