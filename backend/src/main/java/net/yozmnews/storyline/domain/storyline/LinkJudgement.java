package net.yozmnews.storyline.domain.storyline;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.storyline.LinkedBy;
import net.yozmnews.storyline.domain.linking.Asked;
import net.yozmnews.storyline.domain.linking.Score;
import org.jspecify.annotations.Nullable;

/**
 * 소속 하나를 정한 근거 — docs/specs/storyline.md §4 · storyline-R14 · storyline-R36.
 *
 * @param score 점수 · 코사인 · 핵심 말 겹침 — {@code MERGE} 면 없다
 * @param criteriaId 판정에 쓴 기준 이력 ID — {@code MERGE} 면 없다
 * @param asked 띠 안에서 흐름 소속 판정의 확률로 정했으면 그 결과 — 위 기준 이상으로 정했거나 {@code MERGE} 면 없다
 * @param linkedAt 정한 시각
 */
public record LinkJudgement(LinkedBy linkedBy, @Nullable Score score, @Nullable Long criteriaId, @Nullable Asked asked,
		Instant linkedAt) {

	/**
	 * @throws IllegalArgumentException {@code MERGE} 에 점수 · 기준 · 판정 결과가 있거나, 그 밖에 점수 · 기준이 없거나, 판정 결과에 확률이
	 * 없을 때
	 */
	public LinkJudgement {
		Objects.requireNonNull(linkedBy, "linkedBy");
		Objects.requireNonNull(linkedAt, "linkedAt");
		boolean judged = switch (linkedBy) {
			case RULE, PAIR, GROUP -> true;
			case MERGE -> false;
		};
		if (judged != (score != null) || judged != (criteriaId != null)) {
			throw new IllegalArgumentException(linkedBy + " 의 점수 · 기준이 맞지 않다: " + score + " · " + criteriaId);
		}
		if (asked != null && (!judged || !asked.answered())) {
			throw new IllegalArgumentException(linkedBy + " 의 판정 결과가 맞지 않다: " + asked);
		}
	}

	/**
	 * 판정으로 정한 소속.
	 * @param asked 띠 안에서 확률로 정했으면 그 결과, 위 기준 이상이면 null
	 */
	public static LinkJudgement judged(LinkedBy linkedBy, Score score, long criteriaId, @Nullable Asked asked,
			Instant linkedAt) {
		return new LinkJudgement(linkedBy, score, criteriaId, asked, linkedAt);
	}

	/** 합쳐진 사건에서 옮긴 소속 */
	public static LinkJudgement merged(Instant linkedAt) {
		return new LinkJudgement(LinkedBy.MERGE, null, null, null, linkedAt);
	}

}
