package net.yozmnews.evaluation.domain.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 피드 맥락 — 카테고리 상한과 그 상한을 적용하는 팩터리.
 * <p>
 * 지키는 규칙: evaluation-R17
 */
class FeedContextTests {

	/** 한자 확장 B 의 첫 글자 — 코드 포인트 하나가 UTF-16 두 단위(서로게이트 쌍)다 */
	private static final String SUPPLEMENTARY = "𠀀";

	@Test
	void acceptsMissingSourceNameAndSection() {
		FeedContext context = new FeedContext(null, null, List.of());

		assertThat(context.sourceName()).isNull();
		assertThat(context.section()).isNull();
	}

	@Test
	void refusesBlankSourceNameOrSection() {
		assertThatIllegalArgumentException().isThrownBy(() -> new FeedContext(" ", "정치", List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new FeedContext("동아일보", "", List.of()));
	}

	@Test
	void acceptsFiveCategoriesAndRefusesSix() {
		assertThat(new FeedContext(null, null, List.of("a", "b", "c", "d", "e")).categories()).hasSize(5);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FeedContext(null, null, List.of("a", "b", "c", "d", "e", "f")));
	}

	@Test
	void countsCategoryLengthInCodePoints() {
		assertThat(new FeedContext(null, null, List.of("가".repeat(40))).categories()).hasSize(1);
		assertThatIllegalArgumentException().isThrownBy(() -> new FeedContext(null, null, List.of("가".repeat(41))));
		// 서로게이트 쌍 40개는 UTF-16 단위로 80 이지만 코드 포인트로는 40 이다
		assertThat(new FeedContext(null, null, List.of(SUPPLEMENTARY.repeat(40))).categories()).hasSize(1);
	}

	@Test
	void refusesABlankCategory() {
		assertThatIllegalArgumentException().isThrownBy(() -> new FeedContext(null, null, List.of("정치", " ")));
	}

	@Test
	void limitedKeepsTheFirstFiveCategories() {
		FeedContext context = FeedContext.limited(null, null, List.of("a", "b", "c", "d", "e", "f", "g"));

		assertThat(context.categories()).containsExactly("a", "b", "c", "d", "e");
	}

	@Test
	void limitedCutsALongCategoryToFortyCodePoints() {
		FeedContext context = FeedContext.limited(null, null, List.of("가".repeat(45)));

		assertThat(context.categories()).containsExactly("가".repeat(40));
	}

	@Test
	void limitedDoesNotSplitASurrogatePair() {
		FeedContext context = FeedContext.limited(null, null, List.of(SUPPLEMENTARY.repeat(45)));

		assertThat(context.categories()).containsExactly(SUPPLEMENTARY.repeat(40));
	}

	@Test
	void limitedDropsBlankCategoriesBeforeCounting() {
		FeedContext context = FeedContext.limited(null, null, List.of("a", " ", "b", "c", "d", "e", "f"));

		assertThat(context.categories()).containsExactly("a", "b", "c", "d", "e");
	}

	@Test
	void limitedStripsTheTrailingSpaceOfACutCategory() {
		// 40번째 코드 포인트가 공백이면 자른 끝에 공백이 남는다
		FeedContext context = FeedContext.limited(null, null, List.of("가".repeat(39) + " 나다"));

		assertThat(context.categories()).containsExactly("가".repeat(39));
	}

	@Test
	void limitedDropsACategoryThatIsBlankAfterCutting() {
		FeedContext context = FeedContext.limited(null, null, List.of(" ".repeat(40) + "정치", "사회"));

		assertThat(context.categories()).containsExactly("사회");
	}

	@Test
	void limitedTurnsBlankSourceNameAndSectionIntoNull() {
		FeedContext context = FeedContext.limited(" ", "\t", List.of());

		assertThat(context.sourceName()).isNull();
		assertThat(context.section()).isNull();
		assertThat(FeedContext.limited("동아일보", "정치", List.of()))
			.isEqualTo(new FeedContext("동아일보", "정치", List.of()));
	}

}
