package net.yozmnews.source.domain.registry;

import static net.yozmnews.source.domain.registry.RegistryFixtures.UNREVIEWED_TEXT;
import static net.yozmnews.source.domain.registry.RegistryFixtures.profile;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.source.domain.profile.FeedKey;
import org.junit.jupiter.api.Test;

/** 기동 대조의 판정 — 프로필 {one, two} 와 저장된 피드 키를 견준다. */
class FeedReconciliationTests {

	@Test
	void findsFeedsAddedToTheProfileAndFeedsDroppedFromIt() {
		FeedReconciliation reconciliation = FeedReconciliation.compare(profile(UNREVIEWED_TEXT),
				List.of(new FeedKey("two"), new FeedKey("three")));

		assertThat(reconciliation.missing()).containsExactly(new FeedKey("one"));
		assertThat(reconciliation.orphaned()).containsExactly(new FeedKey("three"));
	}

	@Test
	void findsNothingWhenTheyMatch() {
		FeedReconciliation reconciliation = FeedReconciliation.compare(profile(UNREVIEWED_TEXT),
				List.of(new FeedKey("one"), new FeedKey("two")));

		assertThat(reconciliation.missing()).isEmpty();
		assertThat(reconciliation.orphaned()).isEmpty();
	}

}
