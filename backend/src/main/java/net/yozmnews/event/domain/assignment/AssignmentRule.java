package net.yozmnews.event.domain.assignment;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 배정 판정의 정본 — 처음 판정과 재판정이 같은 함수를 쓴다 · docs/specs/event.md §4 · event-R11 · event-R18.
 * <ol>
 * <li>점수가 가장 높은 후보(같으면 사건 ID 가 작은 것)가 위 기준 이상이면 산식으로 붙인다</li>
 * <li>띠(아래 기준 ≤ 점수 &lt; 위 기준)의 후보 가운데 확률이 가장 높은 것(같으면 점수가 높은 것, 그다음 사건 ID 가 작은 것)이 확률 기준
 * 이상이면 판정으로 붙인다</li>
 * <li>띠에 확률 없는 후보가 있으면 — 처음 판정은 그 후보를 묻고, 판정 뒤에는 붙일 수 없는 것으로 보고, 재판정은 지금 배정을 유지한다</li>
 * <li>그 밖에는 새 사건을 연다 — 띠에 확률이 하나라도 있으면 판정이, 없으면 산식이 정한 것이다</li>
 * </ol>
 * 경계는 모두 포함한다.
 */
public final class AssignmentRule {

	/** 한 번에 묻는 후보 수의 상한 — 사건 소속 판정의 후보 상한(10) 이하 */
	public static final int MAX_ASKED = 5;

	private static final Comparator<StoredCandidate> BY_SCORE = Comparator
		.comparingDouble(StoredCandidate::score)
		.reversed()
		.thenComparingLong(StoredCandidate::eventId);

	private static final Comparator<StoredCandidate> BY_PROBABILITY = Comparator
		.comparingDouble((StoredCandidate candidate) -> Objects.requireNonNull(candidate.probability()))
		.reversed()
		.thenComparing(BY_SCORE);

	private AssignmentRule() {
	}

	/**
	 * 판정한다.
	 * @param candidates 저장 기준 이상인 후보
	 */
	public static Decision decide(List<StoredCandidate> candidates, AssignmentCriteria criteria, DecisionPhase phase) {
		Optional<StoredCandidate> best = candidates.stream().min(BY_SCORE);
		if (best.isPresent() && best.get().score() >= criteria.upper()) {
			return new Decision.Attach(best.get().eventId(), best.get().score(), DecidedBy.FORMULA);
		}
		List<StoredCandidate> band = candidates.stream()
			.filter(candidate -> candidate.score() >= criteria.lower() && candidate.score() < criteria.upper())
			.toList();
		List<StoredCandidate> judged = band.stream().filter(candidate -> candidate.probability() != null).toList();
		Optional<StoredCandidate> likeliest = judged.stream().min(BY_PROBABILITY);
		if (likeliest.isPresent() && Objects.requireNonNull(likeliest.get().probability()) >= criteria.probability()) {
			return new Decision.Attach(likeliest.get().eventId(), likeliest.get().score(), DecidedBy.EVALUATOR);
		}
		List<StoredCandidate> unasked = band.stream().filter(candidate -> candidate.probability() == null).toList();
		if (!unasked.isEmpty()) {
			Optional<Decision> unaskedDecision = switch (phase) {
				case FIRST -> Optional.of(new Decision.Ask(unasked.stream()
					.sorted(BY_SCORE)
					.limit(MAX_ASKED)
					.map(StoredCandidate::eventId)
					.toList()));
				case REJUDGE -> Optional.of(new Decision.Keep());
				// 묻지 못한 후보는 붙일 수 없다 — 아래로
				case AFTER_JUDGEMENT -> Optional.empty();
			};
			if (unaskedDecision.isPresent()) {
				return unaskedDecision.get();
			}
		}
		Double bestScore = best.map(StoredCandidate::score).orElse(null);
		return new Decision.Open(bestScore, judged.isEmpty() ? DecidedBy.FORMULA : DecidedBy.EVALUATOR);
	}

}
