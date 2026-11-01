package net.yozmnews.article;

import org.jspecify.annotations.Nullable;

/**
 * 엔트리 개수와 비율 — 비율 산식의 정본. docs/specs/article.md §3 · article-R14.
 * <p>
 * 비율은 개수를 더한 뒤 나눈다 — 칸 · 피드를 합칠 때도 {@link #plus} 로 개수를 더하고 비율을 다시 낸다. 엔트리가 없으면 비율이 없다(0 이
 * 아니다).
 *
 * @param entries 엔트리 수
 * @param duplicates 기존 기사로 판정된 엔트리 수 — 판정 종류가 {@code NEW} 가 아니다
 * @param titleMatches 제목 판정으로 붙은 엔트리 수 — 판정 종류가 {@code TITLE} 이다
 * @param estimated 발행 시각을 추정한 엔트리 수
 * @param guidReuses 같은 피드의 같은 guid 가 다른 기사에 먼저 있는데 새 기사가 된 엔트리 수
 */
public record EntryCounts(int entries, int duplicates, int titleMatches, int estimated, int guidReuses) {

	/** 엔트리가 없다 */
	public static final EntryCounts NONE = new EntryCounts(0, 0, 0, 0, 0);

	public EntryCounts {
		if (entries < 0 || duplicates < 0 || titleMatches < 0 || estimated < 0 || guidReuses < 0) {
			throw new IllegalArgumentException("개수는 0 이상이다: " + entries + " · " + duplicates + " · " + titleMatches
					+ " · " + estimated + " · " + guidReuses);
		}
		if (duplicates > entries || estimated > entries) {
			throw new IllegalArgumentException(
					"중복 · 추정 수는 엔트리 수 이하다: " + duplicates + " · " + estimated + " / " + entries);
		}
		if (titleMatches > duplicates) {
			throw new IllegalArgumentException("제목 판정 수는 중복 수 이하다: " + titleMatches + " / " + duplicates);
		}
		if (guidReuses > entries - duplicates) {
			throw new IllegalArgumentException(
					"guid 재사용 수는 새 기사 엔트리 수 이하다: " + guidReuses + " / " + (entries - duplicates));
		}
	}

	/** 두 개수를 더한다 */
	public EntryCounts plus(EntryCounts other) {
		return new EntryCounts(this.entries + other.entries, this.duplicates + other.duplicates,
				this.titleMatches + other.titleMatches, this.estimated + other.estimated,
				this.guidReuses + other.guidReuses);
	}

	/** 중복률 — 엔트리 가운데 기존 기사로 판정된 비율. 엔트리가 없으면 null */
	public @Nullable Double duplicateRate() {
		return ratio(this.duplicates);
	}

	/** 날짜 추정 비율 — 엔트리 가운데 발행 시각을 추정한 비율. 엔트리가 없으면 null */
	public @Nullable Double estimatedRate() {
		return ratio(this.estimated);
	}

	/** 제목 판정 비율 — 엔트리 가운데 제목 판정으로 붙은 비율. 엔트리가 없으면 null */
	public @Nullable Double titleMatchRate() {
		return ratio(this.titleMatches);
	}

	private @Nullable Double ratio(int count) {
		return (this.entries == 0) ? null : (double) count / this.entries;
	}

}
