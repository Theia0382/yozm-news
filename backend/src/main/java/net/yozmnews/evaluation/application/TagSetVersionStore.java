package net.yozmnews.evaluation.application;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.domain.tagset.TagSet;
import net.yozmnews.evaluation.domain.tagset.TagSetDefinition;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdValues;

/**
 * 새 태그 체계 버전 쓰기의 정본 — 버전 행과 그 버전의 처음 태그 기준 · 저장 기준 · 이력 첫 행 — docs/specs/evaluation.md §4 ·
 * evaluation-R2 · evaluation-R3 · evaluation-R13 · evaluation-R16.
 * <p>
 * 트랜잭션을 열지 않는다 — 부르는 쪽이 연 트랜잭션에서 표 잠금({@link TagSetRepository#lockForChange()}) 뒤에 부른다. 빈이 아니다.
 */
final class TagSetVersionStore {

	private final TagSetRepository tagSets;

	private final ThresholdRepository thresholds;

	TagSetVersionStore(TagSetRepository tagSets, ThresholdRepository thresholds) {
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
		this.thresholds = Objects.requireNonNull(thresholds, "thresholds");
	}

	/** 버전 1 — 첫 적재 */
	TagSet first(TagSetDefinition definition, ChangeActor actor, Instant at) {
		return store(TagSet.first(definition.content(), actor, at), definition, actor, at);
	}

	/**
	 * 기준 버전을 끄고 다음 버전을 활성으로 쓴다 — 활성화.
	 * @throws IllegalStateException 기준 버전을 끄지 못했을 때 — 표 잠금 안에서 읽은 활성 버전이다
	 */
	TagSet next(TagSet base, TagSetDefinition definition, ChangeActor actor, Instant at) {
		if (!this.tagSets.deactivate(base.id().value())) {
			throw new IllegalStateException("표 잠금 안에서 읽은 활성 버전을 끄지 못했다: " + base.id().value());
		}
		return store(TagSet.next(base, definition, actor, at), definition, actor, at);
	}

	/** 저장 → 태그 종류마다 처음 값(MultiTagKind 선언 순) */
	private TagSet store(TagSet version, TagSetDefinition definition, ChangeActor actor, Instant at) {
		TagSet saved = this.tagSets.save(version);
		for (MultiTagKind kind : MultiTagKind.values()) {
			ThresholdValues initial = Objects.requireNonNull(definition.initialThresholds().get(kind), "처음 값");
			this.thresholds.insertInitial(saved.id(), kind, initial, actor, at);
		}
		return saved;
	}

}
