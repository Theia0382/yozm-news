package net.yozmnews.digest.domain.interest;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.storyline.StorylineId;

/**
 * 관심사 히스토리에서 스토리라인이 보이고 사라지는 판정의 정본 — 기준은 {@link StorylineLevels} 의 것 그대로다. docs/specs/digest.md §5 · digest-R36
 * <p>
 * 꼭대기는 관심이 만들 기준 이상이면 보이고, 한 번 보인 꼭대기는 관심이 남길 기준 이상인 동안 남는다. 추적은 넣지 않는다 — 관심 값만으로 본다.
 */
public final class StorylineShifts {

	private StorylineShifts() {
	}

	/**
	 * @param shown 이 기준 시각에 보이는 꼭대기
	 * @param entered 앞 기준 시각에 보이지 않다가 보이게 된 꼭대기
	 * @param left 앞 기준 시각에 보이다가 보이지 않게 된 꼭대기
	 */
	public record Shift(Set<StorylineId> shown, Set<StorylineId> entered, Set<StorylineId> left) {

		public Shift {
			shown = Set.copyOf(shown);
			entered = Set.copyOf(entered);
			left = Set.copyOf(left);
		}

	}

	/** 맨 앞(견줌용) 기준 시각에 보이는 꼭대기 — 그 앞을 모르므로 관심이 만들 기준 이상인 것만이다 */
	public static Set<StorylineId> first(StorylineLevels levels) {
		Objects.requireNonNull(levels, "levels");
		return levels.followed(Set.of());
	}

	/**
	 * @param shownBefore 앞 기준 시각에 보이던 꼭대기
	 * @param levels 이 기준 시각의 스토리라인 관심
	 */
	public static Shift between(Set<StorylineId> shownBefore, StorylineLevels levels) {
		Objects.requireNonNull(shownBefore, "shownBefore");
		Objects.requireNonNull(levels, "levels");
		Set<StorylineId> shown = new HashSet<>(levels.followed(Set.of()));
		Set<StorylineId> kept = levels.kept();
		shownBefore.stream().filter(kept::contains).forEach(shown::add);
		Set<StorylineId> entered = new HashSet<>(shown);
		entered.removeAll(shownBefore);
		Set<StorylineId> left = new HashSet<>(shownBefore);
		left.removeAll(shown);
		return new Shift(shown, entered, left);
	}

}
