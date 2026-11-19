package net.yozmnews.digest.domain.content;

import static net.yozmnews.digest.DigestInputs.event;
import static net.yozmnews.digest.DigestInputs.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestInputs;
import net.yozmnews.digest.SectionKind;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.candidate.AliasedItem;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.digest.domain.item.ItemKind;
import net.yozmnews.digest.domain.output.CitedDigest;
import net.yozmnews.digest.domain.output.CitedHeadline;
import net.yozmnews.digest.domain.output.CitedStory;
import org.junit.jupiter.api.Test;

/**
 * 내용 저장 꼴 — 개요 · 주요 뉴스 항목 · 헤드라인 항목의 순서, 섹션마다 인용(항목의 남은 표본 기사)과 섹션 사건(항목의 사건 · 선정 이유).
 * 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R7
 */
class DigestSectionTests {

	/** I1: 사건 30(기사 11 · 12, 큰 슬롯) · 사건 40(기사 13, 구독). I2: 사건 50(기사 14). I3 · I4: 헤드라인 사건 60 · 70 */
	private static final CandidateSet ITEMS = CandidateSet.of(DigestInputs.items(
			item(ItemKind.TOP_STORY, event(30, SelectionReason.MAJOR_STORYLINE, 11, 12),
					event(40, SelectionReason.SUBSCRIBED, 13)),
			item(ItemKind.TOP_STORY, event(50, SelectionReason.INTEREST, 14)),
			item(ItemKind.HEADLINE, event(60, SelectionReason.SUBSCRIBED, 15)),
			item(ItemKind.HEADLINE, event(70, SelectionReason.SUBSCRIBED, 16))));

	@Test
	void layoutsOverviewThenOneSectionPerItem() {
		CitedDigest cited = new CitedDigest("흐름",
				List.of(new CitedStory(aliased("I1"), "하나", "요약 하나"), new CitedStory(aliased("I2"), "둘", "요약 둘")),
				List.of(new CitedHeadline(aliased("I3"), "헤드라인 셋", "한 줄"), new CitedHeadline(aliased("I4"), "헤드라인 넷", null)));

		List<DigestSection> sections = DigestSection.of(new DigestId(9), cited);

		assertThat(sections).extracting(DigestSection::kind, DigestSection::position)
			.containsExactly(tuple(SectionKind.OVERVIEW, 0), tuple(SectionKind.TOP_STORY, 1),
					tuple(SectionKind.TOP_STORY, 2), tuple(SectionKind.HEADLINE, 3), tuple(SectionKind.HEADLINE, 4));
		assertThat(sections.get(0).body()).contains("흐름");
		assertThat(sections.get(0).title()).isEmpty();
		assertThat(sections.get(0).items()).isEmpty();
		assertThat(sections.get(0).events()).isEmpty();
		assertThat(sections.get(1).title()).contains("하나");
		assertThat(sections.get(1).body()).contains("요약 하나");
		// 인용은 항목의 표본 기사 — 기사마다 그 사건의 선정 이유
		assertThat(sections.get(1).items()).containsExactly(new DigestItem(11, SelectionReason.MAJOR_STORYLINE),
				new DigestItem(12, SelectionReason.MAJOR_STORYLINE), new DigestItem(13, SelectionReason.SUBSCRIBED));
		// 섹션 사건은 항목의 사건 순서
		assertThat(sections.get(1).events()).containsExactly(new SectionEvent(30, SelectionReason.MAJOR_STORYLINE),
				new SectionEvent(40, SelectionReason.SUBSCRIBED));
		// 헤드라인은 제목 · 본문(코멘트)
		assertThat(sections.get(3).title()).contains("헤드라인 셋");
		assertThat(sections.get(3).body()).contains("한 줄");
		assertThat(sections.get(3).items()).containsExactly(new DigestItem(15, SelectionReason.SUBSCRIBED));
		assertThat(sections.get(3).events()).containsExactly(new SectionEvent(60, SelectionReason.SUBSCRIBED));
		assertThat(sections.get(4).body()).isEmpty();
		assertThat(sections).allSatisfy(section -> assertThat(section.digestId()).isEqualTo(new DigestId(9)));
	}

	@Test
	void citesOnlyTheSampleArticlesLeftAfterARemovalButKeepsEveryEvent() {
		// 기사 12 를 생성 중에 지웠다 — I1 의 인용은 11 · 13, 섹션 사건은 둘 다
		AliasedItem kept = ITEMS.retain(Set.of(new ArticleId(11), new ArticleId(13))).item("I1").orElseThrow();

		DigestSection story = DigestSection
			.of(new DigestId(9), new CitedDigest("흐름", List.of(new CitedStory(kept, "하나", "s")), List.of()))
			.get(1);

		assertThat(story.items()).extracting(DigestItem::articleId).containsExactly(11L, 13L);
		assertThat(story.events()).extracting(SectionEvent::eventId).containsExactly(30L, 40L);
	}

	@Test
	void layoutsHeadlinesRightAfterTheOverviewWhenThereAreNoStories() {
		CitedDigest cited = new CitedDigest("흐름", List.of(), List.of(new CitedHeadline(aliased("I3"), "h", null)));

		assertThat(DigestSection.of(new DigestId(9), cited)).extracting(DigestSection::kind, DigestSection::position)
			.containsExactly(tuple(SectionKind.OVERVIEW, 0), tuple(SectionKind.HEADLINE, 1));
	}

	@Test
	void knowsWhichKindsCite() {
		assertThat(SectionKind.values()).containsExactly(SectionKind.OVERVIEW, SectionKind.TOP_STORY,
				SectionKind.HEADLINE);
		assertThat(SectionKind.OVERVIEW.cites()).isFalse();
		assertThat(SectionKind.TOP_STORY.cites()).isTrue();
		assertThat(SectionKind.HEADLINE.cites()).isTrue();
	}

	private static AliasedItem aliased(String alias) {
		return ITEMS.item(alias).orElseThrow();
	}

}
