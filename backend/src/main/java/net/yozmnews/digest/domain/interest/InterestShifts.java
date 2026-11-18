package net.yozmnews.digest.domain.interest;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 관심사 히스토리의 판정 — 정본 — docs/specs/digest.md §5
 * <ul>
 * <li>새로 생긴 관심 · 사라진 관심 — 이웃한 두 기간의 관심 있음({@link InterestLevels#present})을 견준다</li>
 * <li>드러난 주제 — 보이는 기간 가운데 한 번이라도 |x| 가 {@link InterestRules#PRESENCE_THRESHOLD} 이상인 중분류(관심 쪽 · 싫어요 쪽 모두)</li>
 * </ul>
 */
public final class InterestShifts {

	private InterestShifts() {
	}

	/**
	 * @param before 앞 기간의 관심
	 * @param after 이번 기간의 관심
	 */
	public static Shift between(InterestLevels before, InterestLevels after) {
		Objects.requireNonNull(before, "before");
		Objects.requireNonNull(after, "after");
		Set<String> entered = new HashSet<>(after.present());
		entered.removeAll(before.present());
		Set<String> left = new HashSet<>(before.present());
		left.removeAll(after.present());
		return new Shift(entered, left);
	}

	/** 드러난 주제 — 주어진 기간 가운데 한 번이라도 |x| ≥ {@link InterestRules#PRESENCE_THRESHOLD} 인 중분류 */
	public static Set<String> prominent(List<InterestLevels> periods) {
		Objects.requireNonNull(periods, "periods");
		Set<String> prominent = new HashSet<>();
		for (InterestLevels levels : periods) {
			levels.levels().forEach((subtopic, level) -> {
				if (Math.abs(level) >= InterestRules.PRESENCE_THRESHOLD) {
					prominent.add(subtopic);
				}
			});
		}
		return Set.copyOf(prominent);
	}

	/**
	 * 이웃한 두 기간 사이의 변화
	 * @param entered 새로 생긴 관심 — 앞 기간에 관심 있음이 아니었고 이번 기간에 관심 있음인 중분류
	 * @param left 사라진 관심 — 앞 기간에 관심 있음이었고 이번 기간에 아닌 중분류
	 */
	public record Shift(Set<String> entered, Set<String> left) {

		public Shift {
			entered = Set.copyOf(Objects.requireNonNull(entered, "entered"));
			left = Set.copyOf(Objects.requireNonNull(left, "left"));
		}

	}

}
