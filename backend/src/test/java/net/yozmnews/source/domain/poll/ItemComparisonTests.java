package net.yozmnews.source.domain.poll;

import static net.yozmnews.source.domain.poll.PollFixtures.article;
import static net.yozmnews.source.domain.poll.PollFixtures.keys;
import static net.yozmnews.source.domain.poll.PollFixtures.linkOnly;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.source.FetchedArticle;
import org.junit.jupiter.api.Test;

/**
 * 새 항목 · 넘침 의심 판정 — 직전 성공 수집의 키와 비교한다. 넘침 의심은 첫 수집이 아니고 직전 · 이번 키가 모두 있는데 하나도 겹치지
 * 않을 때다.
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
	void noOverlapAtAllIsSuspectedOverflow() {
		FetchedArticle c = article("c");
		FetchedArticle d = article("d");

		ItemComparison comparison = ItemComparison.compare(keys("g:a", "g:b"), List.of(c, d));

		assertThat(comparison.newArticles()).containsExactly(c, d);
		assertThat(comparison.overflowSuspected()).isTrue();
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
