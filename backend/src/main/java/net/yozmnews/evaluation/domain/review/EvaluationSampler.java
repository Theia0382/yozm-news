package net.yozmnews.evaluation.domain.review;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 표집의 정본 — 씨앗과 평가 ID 의 해시가 작은 {@code size} 건을 고른다 · docs/specs/evaluation.md §4 · evaluation-R32.
 * <p>
 * 평가 ID {@code id} 의 해시는 씨앗으로 시작한 SplitMix64 의 {@code id + 1} 번째 출력이다 — 상태 {@code seed + (id + 1) × γ} 에 마무리
 * 함수를 씌운다. 해시는 부호 없이 견주고, 같으면 평가 ID 순이다. 한 건씩 뽑는 순서에 기대지 않으므로 모집단에 평가 하나가 들고 나도 많아야
 * 그 평가와 표본 경계의 평가 하나가 바뀐다. 메모리는 {@code size} 에 비례한다.
 */
public final class EvaluationSampler {

	/** SplitMix64 의 증분 γ */
	private static final long GAMMA = 0x9e3779b97f4a7c15L;

	private final long seed;

	private final int size;

	/** 지금까지 가장 작은 {@code size} 건 — 맨 위가 그 가운데 가장 큰 것 */
	private final PriorityQueue<Candidate> kept;

	private long population;

	/**
	 * @throws IllegalArgumentException 크기가 1 미만일 때
	 */
	public EvaluationSampler(long seed, int size) {
		if (size < 1) {
			throw new IllegalArgumentException("표본 크기는 1 이상이다: " + size);
		}
		this.seed = seed;
		this.size = size;
		this.kept = new PriorityQueue<>(size, unsigned().reversed());
	}

	/** 평가 하나를 모집단에 넣는다 */
	public void add(long evaluationId) {
		this.population++;
		Candidate candidate = new Candidate(hash(this.seed, evaluationId), evaluationId);
		if (this.kept.size() < this.size) {
			this.kept.add(candidate);
		}
		else if (unsigned().compare(candidate, this.kept.peek()) < 0) {
			this.kept.poll();
			this.kept.add(candidate);
		}
	}

	/** 고른 평가 ID — 해시 오름차순 */
	public List<Long> chosen() {
		List<Candidate> chosen = new ArrayList<>(this.kept);
		chosen.sort(unsigned());
		return chosen.stream().map(Candidate::id).toList();
	}

	/** 넣은 평가의 수 */
	public long population() {
		return this.population;
	}

	/** 평가 ID 의 해시 — 씨앗으로 시작한 SplitMix64 의 {@code evaluationId + 1} 번째 출력 */
	static long hash(long seed, long evaluationId) {
		long z = seed + (evaluationId + 1) * GAMMA;
		z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
		z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
		return z ^ (z >>> 31);
	}

	/** 해시 순 — 부호 없이, 같으면 평가 ID 순 */
	private static Comparator<Candidate> unsigned() {
		return (a, b) -> {
			int byHash = Long.compareUnsigned(a.hash(), b.hash());
			return (byHash != 0) ? byHash : Long.compare(a.id(), b.id());
		};
	}

	private record Candidate(long hash, long id) {
	}

}
