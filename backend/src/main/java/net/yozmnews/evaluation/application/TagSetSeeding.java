package net.yozmnews.evaluation.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.tagset.TagSet;
import net.yozmnews.evaluation.domain.tagset.TagSetDefinition;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 첫 기동 적재 — docs/specs/evaluation.md §4 · evaluation-R2.
 * <p>
 * 싱글톤을 모두 만든 뒤 · 웹 서버와 작업 서버가 시작하기 전에 돈다 — 어느 역할의 프로세스도 활성 버전 없이 요청 · 작업을 받지 않는다. 역할
 * 애너테이션이 없다(두 역할 공통).
 */
@Component
class TagSetSeeding implements SmartInitializingSingleton {

	private static final Logger logger = LoggerFactory.getLogger(TagSetSeeding.class);

	private final TagSetDefinition definition;

	private final TagSetRepository tagSets;

	private final ThresholdRepository thresholds;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	TagSetSeeding(TagSetDefinition definition, TagSetRepository tagSets, ThresholdRepository thresholds,
			TransactionTemplate transaction) {
		this(definition, tagSets, thresholds, transaction, InstantSource.system());
	}

	TagSetSeeding(TagSetDefinition definition, TagSetRepository tagSets, ThresholdRepository thresholds,
			TransactionTemplate transaction, InstantSource clock) {
		this.definition = definition;
		this.tagSets = tagSets;
		this.thresholds = thresholds;
		this.transaction = transaction;
		this.clock = clock;
	}

	@Override
	public void afterSingletonsInstantiated() {
		seedIfEmpty();
	}

	/**
	 * 버전이 하나도 없을 때만 기본 체계를 버전 1 로 적재한다 — 한 트랜잭션, 표 잠금 안. 다시 불러도 결과가 같다.
	 * @return 적재했는가
	 */
	boolean seedIfEmpty() {
		Boolean seeded = this.transaction.execute(status -> {
			this.tagSets.lockForChange();
			if (this.tagSets.anyExists()) {
				return false;
			}
			Instant now = this.clock.instant();
			TagSetId version = this.tagSets.save(TagSet.first(this.definition.content(), ChangeActor.SYSTEM, now)).id();
			for (Map.Entry<MultiTagKind, ThresholdValues> initial : this.definition.initialThresholds().entrySet()) {
				this.thresholds.insertInitial(version, initial.getKey(), initial.getValue(), ChangeActor.SYSTEM, now);
			}
			return true;
		});
		if (Boolean.TRUE.equals(seeded)) {
			logger.info("기본 태그 체계를 버전 1로 적재했다");
			return true;
		}
		logger.debug("태그 체계 버전이 이미 있어 기본 체계를 적재하지 않았다");
		return false;
	}

}
