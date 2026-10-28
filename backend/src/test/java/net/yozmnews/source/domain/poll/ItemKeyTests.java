package net.yozmnews.source.domain.poll;

import static net.yozmnews.source.domain.poll.PollFixtures.article;
import static net.yozmnews.source.domain.poll.PollFixtures.linkOnly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** 같은 피드 안의 항목 키 — guid 가 먼저이고, 없으면 링크 원문이다. */
class ItemKeyTests {

	@Test
	void usesTheGuidWhenPresent() {
		assertThat(ItemKey.of(article("urn:1", "https://a.example/1"))).isEqualTo(new ItemKey("g:urn:1"));
	}

	@Test
	void usesTheLinkWhenTheGuidIsMissing() {
		assertThat(ItemKey.of(linkOnly("https://a.example/1"))).isEqualTo(new ItemKey("l:https://a.example/1"));
	}

	@Test
	void keepsGuidsAndLinksApartEvenWhenTheyAreEqualText() {
		ItemKey byGuid = ItemKey.of(article("https://a.example/1", "https://a.example/other"));
		ItemKey byLink = ItemKey.of(linkOnly("https://a.example/1"));

		assertThat(byGuid).isNotEqualTo(byLink);
	}

	@Test
	void rejectsAValueWithoutAPrefix() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ItemKey("urn:1"));
	}

}
