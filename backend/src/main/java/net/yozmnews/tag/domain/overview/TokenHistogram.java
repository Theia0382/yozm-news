package net.yozmnews.tag.domain.overview;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.yozmnews.tag.TokenBucket;
import net.yozmnews.tag.TokenSummary;

/**
 * 입력 토큰의 평균 · 최소 · 최대와 칸 — 칸 규칙의 정본 · docs/specs/tag.md §5.
 * <p>
 * 칸 폭은 {@link #WIDTHS} 가운데 칸 수가 {@link #MAX_BUCKETS} 이하가 되는 가장 작은 것이고, 어느 폭도 그 안에 들지 않으면 가장 큰 폭이며
 * 칸 수를 제한하지 않는다. 첫 칸의 아래 끝은 최소를 그 폭으로 내린 값이고, 칸은 최대가 드는 칸까지 빈 칸 없이 이어진다.
 * <p>
 * 폭이 정해지기 전에는 모든 폭의 약수({@link #GRAIN})로 센다 — 메모리는 평가 수가 아니라 토큰 범위에 비례한다.
 */
public final class TokenHistogram {

	/** 칸 폭 사다리 — 작은 것부터 */
	public static final List<Integer> WIDTHS = List.of(100, 250, 500, 1_000, 2_500, 5_000, 10_000);

	/** 칸 수의 상한 — 가장 큰 폭에는 적용하지 않는다 */
	public static final int MAX_BUCKETS = 12;

	/** 세는 눈금 — 모든 폭의 약수 */
	private static final int GRAIN = 50;

	private final TreeMap<Integer, Long> grains = new TreeMap<>();

	private long count;

	private long sum;

	private int min = Integer.MAX_VALUE;

	private int max = Integer.MIN_VALUE;

	/**
	 * 그 입력 토큰을 센다.
	 * @throws IllegalArgumentException 음수일 때
	 */
	public void add(int tokens) {
		if (tokens < 0) {
			throw new IllegalArgumentException("입력 토큰은 음수가 아니다: " + tokens);
		}
		this.grains.merge(tokens / GRAIN, 1L, Long::sum);
		this.count++;
		this.sum += tokens;
		this.min = Math.min(this.min, tokens);
		this.max = Math.max(this.max, tokens);
	}

	/** 센 결과 — 센 것이 없으면 평균 · 최소 · 최대 null, 칸 없음 */
	public TokenSummary summary() {
		if (this.count == 0) {
			return new TokenSummary(null, null, null, List.of());
		}
		int width = width(this.min, this.max);
		int first = this.min / width;
		long[] counts = new long[this.max / width - first + 1];
		for (Map.Entry<Integer, Long> grain : this.grains.entrySet()) {
			counts[(grain.getKey() * GRAIN) / width - first] += grain.getValue();
		}
		List<TokenBucket> buckets = new ArrayList<>(counts.length);
		for (int i = 0; i < counts.length; i++) {
			long lower = (long) (first + i) * width;
			buckets.add(new TokenBucket((int) lower, (int) Math.min(lower + width, Integer.MAX_VALUE), counts[i]));
		}
		return new TokenSummary((double) this.sum / this.count, this.min, this.max, buckets);
	}

	/** 최소 ~ 최대를 덮는 칸 폭 */
	static int width(int min, int max) {
		for (int width : WIDTHS) {
			if (max / width - min / width + 1 <= MAX_BUCKETS) {
				return width;
			}
		}
		return WIDTHS.getLast();
	}

}
