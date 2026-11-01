package net.yozmnews.source.domain.registry;

import static net.yozmnews.source.domain.registry.RegistryFixtures.UNREVIEWED_TEXT;
import static net.yozmnews.source.domain.registry.RegistryFixtures.profile;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.source.domain.profile.FeedKey;
import org.junit.jupiter.api.Test;

/**
 * 기동 대조의 판정 — 프로필 {one, two} 와 저장된 피드 키를 견준다.
 * <p>
 * 지키는 규칙: source-R8
 */
class FeedReconciliationTests {

	@Test
	void findsFeedsAddedToTheProfileAndFeedsDroppedFromIt() {
		FeedReconciliation reconciliation = FeedReconciliation.compare(profile(UNREVIEWED_TEXT),
				List.of(new FeedKey("two"), new FeedKey("three")), List.of());

		assertThat(reconciliation.missing()).containsExactly(new FeedKey("one"));
		assertThat(reconciliation.orphaned()).containsExactly(new FeedKey("three"));
	}

	@Test
	void findsNothingWhenTheyMatch() {
		FeedReconciliation reconciliation = FeedReconciliation.compare(profile(UNREVIEWED_TEXT),
				List.of(new FeedKey("one"), new FeedKey("two")), List.of());

		assertThat(reconciliation.missing()).isEmpty();
		assertThat(reconciliation.orphaned()).isEmpty();
	}

	@Test
	void aHiddenFeedIsNeitherMissingNorOrphanedAndReappearsWhenTheProfileHasIt() {
		FeedReconciliation reconciliation = FeedReconciliation.compare(profile(UNREVIEWED_TEXT),
				List.of(new FeedKey("two")), List.of(new FeedKey("one"), new FeedKey("typo")));

		assertThat(reconciliation.missing()).isEmpty();
		assertThat(reconciliation.orphaned()).isEmpty();
		assertThat(reconciliation.reappeared()).containsExactly(new FeedKey("one"));
	}

	@Test
	void storedFeedsAreSplitByWhetherTheyAreHidden() {
		Feed hiddenTypo = RegistryFixtures.feed("typo", false);
		hiddenTypo.hide(RegistryFixtures.NOW);
		Feed hiddenOne = RegistryFixtures.feed("one", false);
		hiddenOne.hide(RegistryFixtures.NOW);

		FeedReconciliation reconciliation = FeedReconciliation.compare(profile(UNREVIEWED_TEXT),
				List.of(RegistryFixtures.feed("two", false), hiddenTypo, hiddenOne));

		assertThat(reconciliation.missing()).isEmpty();
		assertThat(reconciliation.orphaned()).isEmpty();
		assertThat(reconciliation.reappeared()).containsExactly(new FeedKey("one"));
	}

}
