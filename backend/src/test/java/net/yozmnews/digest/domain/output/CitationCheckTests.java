package net.yozmnews.digest.domain.output;

import static net.yozmnews.digest.DigestInputs.event;
import static net.yozmnews.digest.DigestInputs.item;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestInputs;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.candidate.AliasedItem;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.item.ItemKind;
import org.junit.jupiter.api.Test;

/**
 * 항목 대조 — 모르는 항목 별칭 · 다른 칸의 항목 · 앞에서 이미 쓴 항목은 버리고, 출력에 없는 항목은 센다. 남은 원소는 항목 순서로 둔다. 인용
 * 없음. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R6
 * <p>
 * 항목은 I1 · I2(주요 뉴스 — 사건 1 · 2, 기사 11 · 12 · 13) · I3 · I4(헤드라인 — 기사 14 · 15)이고, 기대값은 손으로 쓴 리터럴이다.
 */
class CitationCheckTests {

	private static final CandidateSet ITEMS = CandidateSet.of(DigestInputs.items(
			item(ItemKind.TOP_STORY, event(1, SelectionReason.SUBSCRIBED, 11)),
			item(ItemKind.TOP_STORY, event(2, SelectionReason.MAJOR_STORYLINE, 12, 13)),
			item(ItemKind.HEADLINE, event(3, SelectionReason.SUBSCRIBED, 14)),
			item(ItemKind.HEADLINE, event(4, SelectionReason.INTEREST, 15))));

	private static final AliasedItem I1 = ITEMS.item("I1").orElseThrow();

	private static final AliasedItem I2 = ITEMS.item("I2").orElseThrow();

	private static final AliasedItem I3 = ITEMS.item("I3").orElseThrow();

	private static final AliasedItem I4 = ITEMS.item("I4").orElseThrow();

	@Test
	void keepsAnOutputThatCoversEveryItemInItsField() {
		DigestDraft draft = new DigestDraft("흐름",
				List.of(new StoryDraft("I1", "t1", "s1"), new StoryDraft("I2", "t2", "s2")),
				List.of(new HeadlineDraft("I3", "h3", "한 줄"), new HeadlineDraft("I4", "h4", null)));

		assertThat(CitationCheck.check(draft, ITEMS)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory(I1, "t1", "s1"), new CitedStory(I2, "t2", "s2")),
						List.of(new CitedHeadline(I3, "h3", "한 줄"), new CitedHeadline(I4, "h4", null))),
				new CitationDrops(0, 0)));
	}

	@Test
	void dropsUnknownItemsAndCountsTheMissingOnes() {
		DigestDraft draft = new DigestDraft("흐름", List.of(new StoryDraft("Z9", "t", "s"), new StoryDraft("i1", "t", "s"),
				new StoryDraft("A1", "기사 별칭", "s"), new StoryDraft("I2", "t2", "s2")), List.of());

		// 남은 것은 I2 하나 — 버린 원소 셋, 출력에 없는 항목 셋(I1 · I3 · I4)
		assertThat(CitationCheck.check(draft, ITEMS)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory(I2, "t2", "s2")), List.of()), new CitationDrops(3, 3)));
	}

	@Test
	void dropsAnItemWrittenInTheOtherField() {
		DigestDraft draft = new DigestDraft("흐름", List.of(new StoryDraft("I1", "t1", "s1"), new StoryDraft("I3", "t3", "s3")),
				List.of(new HeadlineDraft("I2", "h2", null), new HeadlineDraft("I4", "h4", null)));

		// 헤드라인 항목 I3 을 주요 뉴스에, 주요 뉴스 항목 I2 를 헤드라인에 쓴 원소는 버린다
		assertThat(CitationCheck.check(draft, ITEMS)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory(I1, "t1", "s1")),
						List.of(new CitedHeadline(I4, "h4", null))),
				new CitationDrops(2, 2)));
	}

	@Test
	void keepsTheFirstOfARepeatedItem() {
		DigestDraft draft = new DigestDraft("흐름",
				List.of(new StoryDraft("I1", "앞", "s"), new StoryDraft("I1", "뒤", "s")),
				List.of(new HeadlineDraft("I1", "헤드라인으로 또", null), new HeadlineDraft("I3", "앞", null),
						new HeadlineDraft("I3", "뒤", null)));

		assertThat(CitationCheck.check(draft, ITEMS)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory(I1, "앞", "s")), List.of(new CitedHeadline(I3, "앞", null))),
				new CitationDrops(3, 2)));
	}

	@Test
	void putsTheKeptElementsInItemOrderNotInOutputOrder() {
		DigestDraft draft = new DigestDraft("흐름",
				List.of(new StoryDraft("I2", "t2", "s2"), new StoryDraft("I1", "t1", "s1")),
				List.of(new HeadlineDraft("I4", "h4", null), new HeadlineDraft("I3", "h3", null)));

		CitationResult.Cited cited = (CitationResult.Cited) CitationCheck.check(draft, ITEMS);

		assertThat(cited.digest().stories()).extracting(story -> story.item().alias()).containsExactly("I1", "I2");
		assertThat(cited.digest().headlines()).extracting(headline -> headline.item().alias())
			.containsExactly("I3", "I4");
	}

	@Test
	void findsNoCitationWhenNothingIsLeft() {
		DigestDraft draft = new DigestDraft("흐름", List.of(new StoryDraft("Z1", "t", "s")),
				List.of(new HeadlineDraft("I1", "다른 칸", null)));

		assertThat(CitationCheck.check(draft, ITEMS)).isEqualTo(new CitationResult.NoCitation(new CitationDrops(2, 4)));
		assertThat(CitationCheck.check(new DigestDraft("흐름", List.of(), List.of()), ITEMS))
			.isEqualTo(new CitationResult.NoCitation(new CitationDrops(0, 4)));
	}

	@Test
	void treatsAnItemLeftWithoutArticlesAsUnknownAndCitesOnlyTheRemainingSample() {
		// 기사 12 를 그 사이 지웠다 — I2 는 기사 13 만 남고, 기사 15 를 지워 I4 는 빠진다
		CandidateSet present = ITEMS.retain(Set.of(new ArticleId(11), new ArticleId(13), new ArticleId(14)));
		DigestDraft draft = new DigestDraft("흐름", List.of(new StoryDraft("I2", "t2", "s2")),
				List.of(new HeadlineDraft("I4", "h4", null)));

		CitationResult result = CitationCheck.check(draft, present);

		assertThat(result.drops()).isEqualTo(new CitationDrops(1, 2));
		CitedStory story = ((CitationResult.Cited) result).digest().stories().getFirst();
		assertThat(story.item().articles()).extracting(article -> article.articleId().value()).containsExactly(13L);
		assertThat(story.item().events()).hasSize(1);
	}

	@Test
	void treatsAnEmptyAliasAsUnknown() {
		DigestDraft draft = new DigestDraft("흐름", List.of(new StoryDraft("", "t", "s"), new StoryDraft("I1", "t", "s")),
				List.of(new HeadlineDraft("", "h", null)));

		assertThat(CitationCheck.check(draft, ITEMS)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory(I1, "t", "s")), List.of()), new CitationDrops(2, 3)));
	}

}
