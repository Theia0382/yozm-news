package net.yozmnews.storyline.domain.storyline;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.storyline.LinkedBy;
import net.yozmnews.storyline.domain.linking.Score;
import org.jspecify.annotations.Nullable;

/**
 * 소속 하나를 정한 근거 — docs/specs/storyline.md §4 · storyline-R14.
 *
 * @param score 점수 · 코사인 · 핵심 말 겹침 — {@code MERGE} 면 없다
 * @param criteriaId 판정에 쓴 기준 이력 ID — {@code MERGE} 면 없다
 * @param linkedAt 정한 시각
 */
public record LinkJudgement(LinkedBy linkedBy, @Nullable Score score, @Nullable Long criteriaId, Instant linkedAt) {

	/**
	 * @throws IllegalArgumentException {@code MERGE} 에 점수 · 기준이 있거나, 그 밖에 점수 · 기준이 없을 때
	 */
	public LinkJudgement {
		Objects.requireNonNull(linkedBy, "linkedBy");
		Objects.requireNonNull(linkedAt, "linkedAt");
		boolean judged = switch (linkedBy) {
			case RULE, PAIR -> true;
			case MERGE -> false;
		};
		if (judged != (score != null) || judged != (criteriaId != null)) {
			throw new IllegalArgumentException(linkedBy + " 의 점수 · 기준이 맞지 않다: " + score + " · " + criteriaId);
		}
	}

	/** 판정으로 정한 소속 */
	public static LinkJudgement judged(LinkedBy linkedBy, Score score, long criteriaId, Instant linkedAt) {
		return new LinkJudgement(linkedBy, score, criteriaId, linkedAt);
	}

	/** 합쳐진 사건에서 옮긴 소속 */
	public static LinkJudgement merged(Instant linkedAt) {
		return new LinkJudgement(LinkedBy.MERGE, null, null, linkedAt);
	}

}
