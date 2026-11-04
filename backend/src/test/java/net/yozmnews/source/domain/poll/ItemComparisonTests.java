package net.yozmnews.source.domain.poll;

import static net.yozmnews.source.domain.poll.PollFixtures.article;
import static net.yozmnews.source.domain.poll.PollFixtures.articles;
import static net.yozmnews.source.domain.poll.PollFixtures.keys;
import static net.yozmnews.source.domain.poll.PollFixtures.keysOf;
import static net.yozmnews.source.domain.poll.PollFixtures.linkOnly;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.source.FetchedArticle;
import org.junit.jupiter.api.Test;

/**
 * 새 항목 · 넘침 의심 판정 — 직전 키와 이번 기사를 손으로 적거나 건수로 만들어 비교한다. 건수로 만든 입력은 키 수를 함께 단언한다.
 * <p>
 * 지키는 규칙: source-R20 · source-R21
 */
class ItemComparisonTests {

	@Test
	void firstFetchPublishesEverythingWithoutSuspectingOverflow() {
		FetchedArticle a = article("a");
		FetchedArticle b = article("b");

		ItemComparison comparison = ItemComparison.compare(null, List.of(a, b));

		assertThat(comparison.newArticles()).containsExactly(a, b);
		assertThat(comparison.currentKeys()).isEqualTo(keys("g:a", "g:b"));
		assertThat(comparison.overflowSuspected()).isFalse();
	}

	@Test
	void partialOverlapPublishesOnlyTheUnseenItem() {
		FetchedArticle b = article("b");
		FetchedArticle c = article("c");

		ItemComparison comparison = ItemComparison.compare(keys("g:a", "g:b"), List.of(b, c));

		assertThat(comparison.newArticles()).containsExactly(c);
		assertThat(comparison.currentKeys()).isEqualTo(keys("g:b", "g:c"));
		assertThat(comparison.overflowSuspected()).isFalse();
	}

	@Test
	void oneItemReplacedByAnotherIsNotOverflow() {
		FetchedArticle b = article("b");

		ItemComparison comparison = ItemComparison.compare(keys("g:a"), List.of(b));

		assertThat(comparison.newArticles()).containsExactly(b);
		assertThat(comparison.overflowSuspected()).isFalse();
	}

	@Test
	void oneItemFollowedByTwoNewIsNotOverflow() {
		FetchedArticle b = article("b");
		FetchedArticle c = article("c");

		ItemComparison comparison = ItemComparison.compare(keys("g:a"), List.of(b, c));

		assertThat(comparison.newArticles()).containsExactly(b, c);
		assertThat(comparison.overflowSuspected()).isFalse();
	}

	@Test
	void twoItemsBothReplacedAreNotOverflow() {
		FetchedArticle c = article("c");
		FetchedArticle d = article("d");

		ItemComparison comparison = ItemComparison.compare(keys("g:a", "g:b"), List.of(c, d));

		assertThat(comparison.newArticles()).containsExactly(c, d);
		assertThat(comparison.overflowSuspected()).isFalse();
	}

	@Test
	void nineKeysOnEachSideAreBelowTheMinimum() {
		ItemComparison comparison = ItemComparison.compare(keysOf("p", 9), articles("n", 9));

		assertThat(comparison.currentKeys()).hasSize(9);
		assertThat(comparison.overflowSuspected()).isFalse();
	}

	@Test
	void aSideBelowTheMinimumIsNotOverflow() {
		ItemComparison fewerNow = ItemComparison.compare(keysOf("p", 10), articles("n", 9));
		ItemComparison fewerBefore = ItemComparison.compare(keysOf("p", 9), articles("n", 10));

		assertThat(fewerNow.currentKeys()).hasSize(9);
		assertThat(fewerNow.overflowSuspected()).isFalse();
		assertThat(fewerBefore.currentKeys()).hasSize(10);
		assertThat(fewerBefore.overflowSuspected()).isFalse();
	}

	@Test
	void repeatedKeysCountOnceTowardTheMinimum() {
		List<FetchedArticle> fetched = new ArrayList<>(articles("n", 9));
		fetched.add(article("n9", "https://news.example/n9-again"));

		ItemComparison comparison = ItemComparison.compare(keysOf("p", 10), fetched);

		assertThat(comparison.currentKeys()).hasSize(9);
		assertThat(comparison.overflowSuspected()).isFalse();
	}

	@Test
	void tenKeysOnEachSideWithoutOverlapAreSuspectedOverflow() {
		ItemComparison comparison = ItemComparison.compare(keysOf("p", 10), articles("n", 10));

		assertThat(comparison.currentKeys()).hasSize(10);
		assertThat(comparison.overflowSuspected()).isTrue();
	}

	@Test
	void fiftyKeysOnEachSideWithoutOverlapAreSuspectedOverflow() {
		ItemComparison comparison = ItemComparison.compare(keysOf("p", 50), articles("n", 50));

		assertThat(comparison.currentKeys()).hasSize(50);
		assertThat(comparison.overflowSuspected()).isTrue();
	}

	@Test
	void tenKeysSharingOneAreNotOverflow() {
		List<FetchedArticle> fetched = new ArrayList<>(articles("n", 9));
		fetched.add(article("p1"));

		ItemComparison comparison = ItemComparison.compare(keysOf("p", 10), fetched);

		assertThat(comparison.currentKeys()).hasSize(10);
		assertThat(comparison.overflowSuspected()).isFalse();
	}

	@Test
	void anEmptyFeedHasNothingNewAndNoOverflow() {
		ItemComparison comparison = ItemComparison.compare(keys("g:a"), List.of());

		assertThat(comparison.newArticles()).isEmpty();
		assertThat(comparison.currentKeys()).isEmpty();
		assertThat(comparison.overflowSuspected()).isFalse();
	}

	@Test
	void anEmptyPreviousFetchCannotTellOverflow() {
		FetchedArticle a = article("a");

		ItemComparison comparison = ItemComparison.compare(List.of(), List.of(a));

		assertThat(comparison.newArticles()).containsExactly(a);
		assertThat(comparison.overflowSuspected()).isFalse();
	}

	@Test
	void keepsTheFirstOfRepeatedKeysInOneFetch() {
		FetchedArticle first = article("a", "https://news.example/a");
		FetchedArticle repeated = article("a", "https://news.example/a-again");
		FetchedArticle b = article("b");

		ItemComparison comparison = ItemComparison.compare(null, List.of(first, repeated, b));

		assertThat(comparison.newArticles()).containsExactly(first, b);
		assertThat(comparison.currentKeys()).isEqualTo(keys("g:a", "g:b"));
	}

	@Test
	void comparesItemsWithoutAGuidByTheirLink() {
		FetchedArticle seen = linkOnly("https://news.example/1");
		FetchedArticle fresh = linkOnly("https://news.example/2");

		ItemComparison comparison = ItemComparison.compare(keys("l:https://news.example/1"), List.of(seen, fresh));

		assertThat(comparison.newArticles()).containsExactly(fresh);
		assertThat(comparison.overflowSuspected()).isFalse();
	}

}
