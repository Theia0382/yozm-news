package net.yozmnews.digest.domain.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.item.ItemKind;
import net.yozmnews.event.EventId;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.user.SummaryLength;
import org.junit.jupiter.api.Test;

/**
 * 생성의 입력 — 항목 · 사건 · 기사의 순서를 그대로 두고, 같은 사건 · 같은 기사 두 번, 사건 없는 항목, 기사 없는 사건, 사건과 다른 선정 이유의
 * 기사를 거부한다. 요약문이 짧은 항목 수는 0 이상 헤드라인 항목 수 이하다
 */
class DigestInputTests {

	private static final DigestProfile PROFILE = new DigestProfile(List.of("정치"), List.of(), ZoneId.of("Asia/Seoul"),
			SummaryLength.NORMAL, ReasoningEffort.MEDIUM);

	@Test
	void theItemsEventsAndArticlesKeepTheirOrder() {
		InputItem big = new InputItem(ItemKind.TOP_STORY,
				List.of(event(30, SelectionReason.MAJOR_STORYLINE, 7, 3), event(10, SelectionReason.SUBSCRIBED, 1)), null);
		InputItem headline = new InputItem(ItemKind.HEADLINE, List.of(event(20, SelectionReason.INTEREST, 9)), null);

		DigestInput input = new DigestInput(PROFILE, List.of(big, headline), 0);

		assertThat(input.items()).extracting(InputItem::kind).containsExactly(ItemKind.TOP_STORY, ItemKind.HEADLINE);
		assertThat(input.items().getFirst().events()).extracting(InputEvent::event)
			.containsExactly(new EventId(30), new EventId(10));
		assertThat(input.items().getFirst().events().getFirst().articles())
			.extracting(article -> article.candidate().articleId())
			.containsExactly(new ArticleId(7), new ArticleId(3));
	}

	@Test
	void inconsistentInputsAreRefused() {
		// 같은 사건 두 번
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestInput(PROFILE,
				List.of(new InputItem(ItemKind.TOP_STORY, List.of(event(10, SelectionReason.SUBSCRIBED, 1)), null),
						new InputItem(ItemKind.HEADLINE, List.of(event(10, SelectionReason.SUBSCRIBED, 2)), null)), 0));
		// 같은 기사 두 번 — 다른 사건이어도
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestInput(PROFILE,
				List.of(new InputItem(ItemKind.TOP_STORY,
						List.of(event(10, SelectionReason.SUBSCRIBED, 1), event(11, SelectionReason.SUBSCRIBED, 1)), null)), 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new InputItem(ItemKind.TOP_STORY, List.of(), null));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new InputEvent(new EventId(10), SelectionReason.SUBSCRIBED, List.of()));
		// 기사의 선정 이유는 사건의 것이어야 한다
		assertThatIllegalArgumentException().isThrownBy(() -> new InputEvent(new EventId(10),
				SelectionReason.SUBSCRIBED, List.of(article(1, SelectionReason.INTEREST))));
	}

	@Test
	void theShortSummaryItemsStayWithinTheHeadlineItems() {
		// 헤드라인 항목 둘 — 요약문이 짧은 항목 수는 0 ~ 2
		List<InputItem> items = List.of(
				new InputItem(ItemKind.TOP_STORY, List.of(event(10, SelectionReason.SUBSCRIBED, 1)), null),
				new InputItem(ItemKind.HEADLINE, List.of(event(20, SelectionReason.SUBSCRIBED, 2)), null),
				new InputItem(ItemKind.HEADLINE, List.of(event(30, SelectionReason.SUBSCRIBED, 3)), null));

		assertThat(new DigestInput(PROFILE, items, 2).shortSummaryItems()).isEqualTo(2);
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestInput(PROFILE, items, 3));
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestInput(PROFILE, items, -1));
	}

	@Test
	void noItemsIsAnInputToo() {
		assertThat(new DigestInput(PROFILE, List.of(), 0).items()).isEmpty();
	}

	private static InputEvent event(long id, SelectionReason reason, long... articles) {
		return new InputEvent(new EventId(id), reason,
				java.util.Arrays.stream(articles).mapToObj(article -> article(article, reason)).toList());
	}

	private static CandidateArticle article(long id, SelectionReason reason) {
		return new CandidateArticle(new DigestCandidate(new ArticleId(id), reason), "제목 " + id, null, List.of("출처"),
				Instant.parse("2026-11-17T00:00:00Z"), List.of("정치"), List.of());
	}

}
