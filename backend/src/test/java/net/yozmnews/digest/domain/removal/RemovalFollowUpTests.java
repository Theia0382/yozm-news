package net.yozmnews.digest.domain.removal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.SectionKind;
import org.junit.jupiter.api.Test;

/**
 * 지운 기사 따르기의 판단 — 인용이 모두 지워진 인용 섹션, 인용 섹션이 남지 않는 다이제스트의 개요. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R10
 */
class RemovalFollowUpTests {

	private static ArticleId a(long id) {
		return new ArticleId(id);
	}

	@Test
	void deletesACitedSectionWhoseCitationsAreAllRemovedAndKeepsTheRest() {
		List<SectionCitations> digest = List.of(new SectionCitations(100, 1, SectionKind.OVERVIEW, Set.of()),
				new SectionCitations(101, 1, SectionKind.TOP_STORY, Set.of(a(11))),
				new SectionCitations(102, 1, SectionKind.TOP_STORY, Set.of(a(11), a(12))),
				new SectionCitations(103, 1, SectionKind.HEADLINE, Set.of(a(13))));

		assertThat(RemovalFollowUp.sectionsToDelete(digest, Set.of(a(11)))).containsExactly(101L);
	}

	@Test
	void deletesTheOverviewWhenNoCitedSectionRemains() {
		List<SectionCitations> digest = List.of(new SectionCitations(100, 1, SectionKind.OVERVIEW, Set.of()),
				new SectionCitations(101, 1, SectionKind.TOP_STORY, Set.of(a(11))),
				new SectionCitations(103, 1, SectionKind.HEADLINE, Set.of(a(12), a(13))));

		assertThat(RemovalFollowUp.sectionsToDelete(digest, Set.of(a(11), a(12), a(13))))
			.containsExactlyInAnyOrder(100L, 101L, 103L);
	}

	@Test
	void keepsTheOverviewWhileOneCitedSectionRemains() {
		List<SectionCitations> digest = List.of(new SectionCitations(100, 1, SectionKind.OVERVIEW, Set.of()),
				new SectionCitations(101, 1, SectionKind.TOP_STORY, Set.of(a(11))),
				new SectionCitations(103, 1, SectionKind.HEADLINE, Set.of(a(12), a(13))));

		assertThat(RemovalFollowUp.sectionsToDelete(digest, Set.of(a(11), a(12))))
			.containsExactlyInAnyOrder(101L);
	}

	@Test
	void leavesADigestThatDoesNotCiteARemovedArticle() {
		List<SectionCitations> sections = List.of(new SectionCitations(100, 1, SectionKind.OVERVIEW, Set.of()),
				new SectionCitations(101, 1, SectionKind.TOP_STORY, Set.of(a(11))),
				new SectionCitations(200, 2, SectionKind.OVERVIEW, Set.of()),
				new SectionCitations(201, 2, SectionKind.TOP_STORY, Set.of(a(21))));

		assertThat(RemovalFollowUp.sectionsToDelete(sections, Set.of(a(11))))
			.containsExactlyInAnyOrder(100L, 101L);
	}

	@Test
	void deletesNothingWhenNothingIsCited() {
		assertThat(RemovalFollowUp.sectionsToDelete(List.of(), Set.of(a(11)))).isEmpty();
	}

}
