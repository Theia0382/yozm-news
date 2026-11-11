package net.yozmnews.storyline.domain.storyline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 정돈 판정 — 구성 사건이 없으면 지우고, 사건 하나면 그 사건을 가진 스토리라인 가운데 ID 가 가장 작은 것에 합치거나 풀고, 사건 둘 이상이면
 * 구성 사건이 같은 것끼리 ID 가 가장 작은 것에 합친다. 사건 둘 이상의 포함은 그대로 둔다.
 * <p>
 * 지키는 규칙: storyline-R13 · storyline-R17
 */
class TidyingTests {

	@Test
	void emptyIsRemovedAndASingleEventIsDissolvedOrMerged() {
		assertThat(Tidying.decide(4, List.of(), Map.of())).isEqualTo(new Tidying.Remove());
		assertThat(Tidying.decide(4, List.of(5L), Map.of())).isEqualTo(new Tidying.Dissolve(5));
		assertThat(Tidying.decide(4, List.of(5L), Map.of(9L, List.of(5L, 7L), 3L, List.of(5L, 6L))))
			.isEqualTo(new Tidying.MergeInto(3, List.of(4L)));
		// 남은 9 도 사건 하나짜리다 — 같은 정돈에서 9 가 풀린다
		assertThat(Tidying.decide(4, List.of(5L), Map.of(9L, List.of(5L)))).isEqualTo(new Tidying.MergeInto(9, List.of(4L)));
	}

	@Test
	void onlyTheSameEventsMerge() {
		assertThat(Tidying.decide(4, List.of(5L, 6L), Map.of())).isEqualTo(new Tidying.Keep());
		// 사건 둘 이상의 포함은 갈래일 수 있다 — 합치지 않는다
		assertThat(Tidying.decide(4, List.of(5L, 6L), Map.of(7L, List.of(5L, 6L, 8L)))).isEqualTo(new Tidying.Keep());
		assertThat(Tidying.decide(4, List.of(5L, 6L),
				Map.of(3L, List.of(5L, 6L), 9L, List.of(5L, 6L), 7L, List.of(5L, 6L, 8L))))
			.isEqualTo(new Tidying.MergeInto(3, List.of(4L, 9L)));
		assertThat(Tidying.decide(2, List.of(5L, 6L), Map.of(3L, List.of(5L, 6L))))
			.isEqualTo(new Tidying.MergeInto(2, List.of(3L)));
	}

	@Test
	void aMergeNeverNamesTheSurvivorOrNothing() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Tidying.MergeInto(3, List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new Tidying.MergeInto(3, List.of(3L, 4L)));
		assertThatIllegalArgumentException().isThrownBy(() -> Tidying.decide(4, List.of(5L), Map.of(4L, List.of(5L))));
	}

}
