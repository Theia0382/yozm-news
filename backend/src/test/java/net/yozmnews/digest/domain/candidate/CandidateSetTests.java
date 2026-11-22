package net.yozmnews.digest.domain.candidate;

import static net.yozmnews.digest.DigestInputs.event;
import static net.yozmnews.digest.DigestInputs.item;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestInputs;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.content.SectionEvent;
import net.yozmnews.digest.domain.item.ItemKind;
import org.junit.jupiter.api.Test;

/**
 * 항목 · 기사와 별칭 · 후보 행의 보존 기한. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R3 · digest-R11
 */
class CandidateSetTests {

	private static final DigestCandidate C7 = new DigestCandidate(new ArticleId(7), SelectionReason.MAJOR_STORYLINE);

	private static final DigestCandidate C3 = new DigestCandidate(new ArticleId(3), SelectionReason.MAJOR_STORYLINE);

	private static final DigestCandidate C1 = new DigestCandidate(new ArticleId(1), SelectionReason.SUBSCRIBED);

	private static final DigestCandidate C5 = new DigestCandidate(new ArticleId(5), SelectionReason.INTEREST);

	/** 항목 I1(주요 뉴스): 사건 30(기사 7 · 3) · 사건 10(기사 1), 항목 I2(헤드라인): 사건 20(기사 5) */
	private static CandidateSet set() {
		return CandidateSet.of(DigestInputs.items(
				item(ItemKind.TOP_STORY, event(30, SelectionReason.MAJOR_STORYLINE, 7, 3),
						event(10, SelectionReason.SUBSCRIBED, 1)),
				item(ItemKind.HEADLINE, event(20, SelectionReason.INTEREST, 5))));
	}

	@Test
	void aliasesArticlesAndItemsInInputOrder() {
		CandidateSet set = set();

		assertThat(set.entries()).containsExactly(new AliasedCandidate("A1", C7), new AliasedCandidate("A2", C3),
				new AliasedCandidate("A3", C1), new AliasedCandidate("A4", C5));
		assertThat(set.items()).containsExactly(
				new AliasedItem("I1", ItemKind.TOP_STORY,
						List.of(new SectionEvent(30, SelectionReason.MAJOR_STORYLINE),
								new SectionEvent(10, SelectionReason.SUBSCRIBED)),
						List.of(C7, C3, C1), null),
				new AliasedItem("I2", ItemKind.HEADLINE, List.of(new SectionEvent(20, SelectionReason.INTEREST)),
						List.of(C5), null));
		assertThat(set.item("I2")).map(AliasedItem::kind).contains(ItemKind.HEADLINE);
		assertThat(set.item("i2")).isEmpty();
		assertThat(set.item("I3")).isEmpty();
		assertThat(set.item("A1")).as("기사 별칭은 항목이 아니다").isEmpty();
		assertThat(set.articleIds()).containsExactly(new ArticleId(7), new ArticleId(3), new ArticleId(1),
				new ArticleId(5));
	}

	@Test
	void theEventCountIsTheNumberOfEventsNotArticles() {
		CandidateSet set = set();

		assertThat(set.eventCount()).isEqualTo(3);
		assertThat(set.size()).isEqualTo(4);
	}

	@Test
	void keepsAliasesWhenRetainingAndDropsItemsLeftWithoutArticles() {
		CandidateSet kept = set().retain(Set.of(new ArticleId(3), new ArticleId(1)));

		assertThat(kept.entries()).containsExactly(new AliasedCandidate("A2", C3), new AliasedCandidate("A3", C1));
		// 항목 I1 은 기사가 좁혀지고 사건은 그대로, 기사가 남지 않은 I2 는 빠진다
		assertThat(kept.items()).containsExactly(new AliasedItem("I1", ItemKind.TOP_STORY,
				List.of(new SectionEvent(30, SelectionReason.MAJOR_STORYLINE),
						new SectionEvent(10, SelectionReason.SUBSCRIBED)),
				List.of(C3, C1), null));
		assertThat(kept.item("I2")).isEmpty();
		assertThat(kept.size()).isEqualTo(2);
	}

	@Test
	void allowsAnEmptySet() {
		CandidateSet empty = CandidateSet.of(DigestInputs.items());

		assertThat(empty.isEmpty()).isTrue();
		assertThat(empty.items()).isEmpty();
		assertThat(empty.eventCount()).isZero();
		assertThat(empty.articleIds()).isEmpty();
		assertThat(set().retain(Set.of()).isEmpty()).isTrue();
		assertThat(set().retain(Set.of()).items()).isEmpty();
	}

	@Test
	void cutsOffThirtyDaysBeforeNow() {
		assertThat(CandidateRetention.cutoff(Instant.parse("2026-11-16T00:00:00Z")))
			.isEqualTo(Instant.parse("2026-10-17T00:00:00Z"));
	}

}
