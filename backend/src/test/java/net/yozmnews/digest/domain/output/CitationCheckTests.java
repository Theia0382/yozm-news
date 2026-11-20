package net.yozmnews.digest.domain.output;

import static net.yozmnews.digest.DigestInputs.event;
import static net.yozmnews.digest.DigestInputs.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestInputs;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.candidate.AliasedItem;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.item.ItemKind;
import org.junit.jupiter.api.Test;

/**
 * 항목 대조 — 항목 순서대로 그 칸에서 항목 별칭의 칸을 찾고, 다른 칸의 별칭 · 모르는 별칭은 찾지 않으며, 칸이 없는 항목은 첫 인용 기사 제목의
 * 헤드라인으로 대체한다. 인용 없음은 대조할 항목이 없을 때뿐이다. 스프링 없이 돈다.
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

	/** 항목들의 기사 제목 — 손으로 쓴 리터럴 */
	private static final Map<ArticleId, String> TITLES = Map.of(new ArticleId(11), "기사 열하나 제목",
			new ArticleId(12), "기사 열둘 제목", new ArticleId(13), "기사 열셋 제목", new ArticleId(14), "기사 열넷 제목",
			new ArticleId(15), "기사 열다섯 제목");

	@Test
	void keepsAnOutputThatCoversEveryItemInItsField() {
		DigestDraft draft = new DigestDraft("흐름",
				stories("I1", new StoryDraft("t1", "s1"), "I2", new StoryDraft("t2", "s2")),
				headlines("I3", new HeadlineDraft("h3", "한 줄"), "I4", new HeadlineDraft("h4", null)));

		assertThat(CitationCheck.check(draft, ITEMS, TITLES)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory(I1, "t1", "s1"), new CitedStory(I2, "t2", "s2")),
						List.of(new CitedHeadline(I3, "h3", "한 줄"), new CitedHeadline(I4, "h4", null))),
				new CitationDrops(0)));
	}

	@Test
	void anItemWithoutItsFieldStaysAsAHeadlineWithTheTitleOfItsFirstCitedArticle() {
		// 주요 뉴스 항목 I2(기사 12 · 13)의 칸이 없다 — I2 는 헤드라인으로, 제목은 첫 인용 기사 12 의 제목, 코멘트 없음.
		// 생성에 넘긴 항목은 주요 뉴스 다음 헤드라인이라 내린 I2 는 헤드라인 칸의 앞에 온다
		DigestDraft draft = new DigestDraft("흐름", stories("I1", new StoryDraft("t1", "s1")),
				headlines("I3", new HeadlineDraft("h3", null), "I4", new HeadlineDraft("h4", null)));

		assertThat(CitationCheck.check(draft, ITEMS, TITLES)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory(I1, "t1", "s1")),
						List.of(new CitedHeadline(I2, "기사 열둘 제목", null), new CitedHeadline(I3, "h3", null),
								new CitedHeadline(I4, "h4", null))),
				new CitationDrops(1)));
	}

	@Test
	void doesNotLookInTheOtherFieldOrAtUnknownKeysAndSubstitutesWhatIsMissing() {
		// 주요 뉴스 칸에 I2 와 헤드라인 항목 I3 · 모르는 키 Z9, 헤드라인 칸(원소의 별칭)에 주요 뉴스 항목 I1 · 모르는 별칭 Z9 · 빈 별칭 —
		// 칸이 있는 것은 I2 하나, I1 · I3 · I4 는 대체 헤드라인
		DigestDraft draft = new DigestDraft("흐름",
				stories("I2", new StoryDraft("t2", "s2"), "I3", new StoryDraft("t3", "s3"), "Z9", new StoryDraft("t", "s")),
				headlines("I1", new HeadlineDraft("h1", null), "Z9", new HeadlineDraft("h", null), "",
						new HeadlineDraft("h", null)));

		assertThat(CitationCheck.check(draft, ITEMS, TITLES)).isEqualTo(new CitationResult.Cited(
				new CitedDigest("흐름", List.of(new CitedStory(I2, "t2", "s2")),
						List.of(new CitedHeadline(I1, "기사 열하나 제목", null), new CitedHeadline(I3, "기사 열넷 제목", null),
								new CitedHeadline(I4, "기사 열다섯 제목", null))),
				new CitationDrops(3)));
	}

	@Test
	void putsTheKeptItemsInItemOrderNotInOutputOrder() {
		DigestDraft draft = new DigestDraft("흐름",
				stories("I2", new StoryDraft("t2", "s2"), "I1", new StoryDraft("t1", "s1")),
				headlines("I4", new HeadlineDraft("h4", null), "I3", new HeadlineDraft("h3", null)));

		CitationResult.Cited cited = (CitationResult.Cited) CitationCheck.check(draft, ITEMS, TITLES);

		assertThat(cited.digest().stories()).extracting(story -> story.item().alias()).containsExactly("I1", "I2");
		assertThat(cited.digest().headlines()).extracting(headline -> headline.item().alias())
			.containsExactly("I3", "I4");
	}

	@Test
	void findsNoCitationOnlyWhenThereIsNoItemLeft() {
		// 모든 기사를 그 사이 지웠다 — 대조할 항목이 없다
		CandidateSet none = ITEMS.retain(Set.of());

		assertThat(CitationCheck.check(new DigestDraft("흐름", Map.of(), Map.of()), none, TITLES))
			.isEqualTo(new CitationResult.NoCitation(new CitationDrops(0)));
		// 대조 — 칸이 하나도 없어도 항목이 있으면 모두 대체 헤드라인이다
		assertThat(CitationCheck.check(new DigestDraft("흐름", Map.of(), Map.of()), ITEMS, TITLES))
			.isInstanceOf(CitationResult.Cited.class);
	}

	@Test
	void treatsAnItemLeftWithoutArticlesAsUnknownAndCitesOnlyTheRemainingSample() {
		// 기사 12 를 그 사이 지웠다 — I2 는 기사 13 만 남고, 기사 15 를 지워 I4 는 빠진다
		CandidateSet present = ITEMS.retain(Set.of(new ArticleId(11), new ArticleId(13), new ArticleId(14)));
		DigestDraft draft = new DigestDraft("흐름", stories("I1", new StoryDraft("t1", "s1"), "I2", new StoryDraft("t2", "s2")),
				headlines("I3", new HeadlineDraft("h3", null), "I4", new HeadlineDraft("h4", null)));

		CitationResult result = CitationCheck.check(draft, present, TITLES);

		// 남은 항목 I1 · I2 · I3 은 모두 칸이 있다 — I4 의 칸은 모르는 키라 대체도 없다
		assertThat(result.drops()).isEqualTo(new CitationDrops(0));
		CitedStory story = ((CitationResult.Cited) result).digest().stories().get(1);
		assertThat(story.item().articles()).extracting(article -> article.articleId().value()).containsExactly(13L);
		assertThat(story.item().events()).hasSize(1);
	}

	@Test
	void aSubstitutedItemWhoseFirstArticleWasDeletedTakesTheTitleOfItsFirstRemainingArticle() {
		// 기사 12 를 그 사이 지웠다 — I2 의 인용은 기사 13 만 남는다. I2 의 칸이 없으면 제목은 남은 첫 인용 기사 13 의 제목이다
		CandidateSet present = ITEMS
			.retain(Set.of(new ArticleId(11), new ArticleId(13), new ArticleId(14), new ArticleId(15)));
		DigestDraft draft = new DigestDraft("흐름", stories("I1", new StoryDraft("t1", "s1")),
				headlines("I3", new HeadlineDraft("h3", null), "I4", new HeadlineDraft("h4", null)));

		CitationResult.Cited cited = (CitationResult.Cited) CitationCheck.check(draft, present, TITLES);

		assertThat(cited.digest().headlines()).extracting(headline -> headline.item().alias() + ":" + headline.title())
			.containsExactly("I2:기사 열셋 제목", "I3:h3", "I4:h4");
		assertThat(cited.drops()).isEqualTo(new CitationDrops(1));
	}

	@Test
	void aSubstitutedItemWhoseArticleHasNoTitleFailsLoudly() {
		DigestDraft draft = new DigestDraft("흐름", Map.of(), Map.of());

		assertThatNullPointerException().isThrownBy(() -> CitationCheck.check(draft, ITEMS, Map.of()));
	}

	private static Map<String, StoryDraft> stories(Object... aliasAndDraft) {
		Map<String, StoryDraft> map = new LinkedHashMap<>();
		for (int i = 0; i < aliasAndDraft.length; i += 2) {
			map.put((String) aliasAndDraft[i], (StoryDraft) aliasAndDraft[i + 1]);
		}
		return map;
	}

	private static Map<String, HeadlineDraft> headlines(Object... aliasAndDraft) {
		Map<String, HeadlineDraft> map = new LinkedHashMap<>();
		for (int i = 0; i < aliasAndDraft.length; i += 2) {
			map.put((String) aliasAndDraft[i], (HeadlineDraft) aliasAndDraft[i + 1]);
		}
		return map;
	}

}
