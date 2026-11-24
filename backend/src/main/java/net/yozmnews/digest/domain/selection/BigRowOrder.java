package net.yozmnews.digest.domain.selection;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import net.yozmnews.digest.domain.interest.InterestRules;
import net.yozmnews.digest.domain.interest.InterestWeights;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigEvent;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigRow;

/**
 * 큰 판정 행의 순서 — 정본 — docs/specs/digest.md §5 · digest-R19
 * <p>
 * 행 점수는 보도 강도 × 그 행 후보 사건들의 관심 가중치({@link InterestWeights#weightOf} — 구독 후보 순위와 같은 주제 × 스토리라인 가중치)의
 * 산술 평균이다. 후보가 없는 사건은 평균에 넣지 않고, 후보가 하나도 없는 행은 중립 가중치다 — 고르기({@link BigSlotPicker#pick})가 어차피
 * 건너뛴다. 평균은 행의 후보 사건 전부다(고르기가 행마다 담는 사건과 다를 수 있다). 점수 내림차순, 같으면 받은 순서(큰 판정 순서)다.
 */
public final class BigRowOrder {

	private BigRowOrder() {
	}

	/**
	 * @param rows 큰 판정 행 — 큰 판정 목록의 순서
	 * @param weights 그 사용자의 관심 가중치
	 * @return 관심을 곱한 순서의 행
	 */
	public static List<BigRow> order(List<BigRow> rows, InterestWeights weights) {
		Objects.requireNonNull(rows, "rows");
		Objects.requireNonNull(weights, "weights");
		double[] scores = rows.stream()
			.mapToDouble(row -> row.intensity().doubleValue() * weightOf(row, weights))
			.toArray();
		return IntStream.range(0, rows.size())
			.boxed()
			.sorted(Comparator.comparingDouble((Integer index) -> scores[index]).reversed())
			.map(rows::get)
			.toList();
	}

	private static double weightOf(BigRow row, InterestWeights weights) {
		return row.events()
			.stream()
			.map(BigEvent::candidate)
			.filter(Objects::nonNull)
			.mapToDouble(candidate -> weights.weightOf(candidate.facts().subtopics(), candidate.tops()))
			.average()
			.orElse(InterestRules.NEUTRAL);
	}

}
