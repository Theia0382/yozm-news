package net.yozmnews.evaluation.application;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.domain.tagset.TagSetContent;
import net.yozmnews.evaluation.domain.tagset.TagSetDefinition;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
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

	private final TagSetVersionStore versionStore;

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
		this.versionStore = new TagSetVersionStore(tagSets, thresholds);
		this.transaction = transaction;
		this.clock = clock;
	}

	@Override
	public void afterSingletonsInstantiated() {
		seedIfEmpty();
	}

	/**
	 * 버전이 하나도 없을 때만 기본 체계를 버전 1 로 적재한다 — 한 트랜잭션, 표 잠금 안. 다시 불러도 결과가 같다. 적재하지 않았고 기본
	 * 체계 파일과 내용이 같은 버전이 없으면 정보 로그를 남긴다(evaluation-R2).
	 * @return 적재했는가
	 */
	boolean seedIfEmpty() {
		Boolean seeded = this.transaction.execute(status -> {
			this.tagSets.lockForChange();
			if (this.tagSets.anyExists()) {
				return false;
			}
			this.versionStore.first(this.definition, ChangeActor.SYSTEM, this.clock.instant());
			return true;
		});
		if (Boolean.TRUE.equals(seeded)) {
			logger.info("기본 태그 체계를 버전 1로 적재했다");
			return true;
		}
		logger.debug("태그 체계 버전이 이미 있어 기본 체계를 적재하지 않았다");
		List<TagSetContent> stored = new ArrayList<>();
		this.tagSets.findAll().forEach(tagSet -> stored.add(tagSet.content()));
		if (!this.definition.content().matchesAny(stored)) {
			logger.info("기본 체계 파일과 내용이 같은 태그 체계 버전이 없다 — 파일의 개정을 들이려면 체계 변경으로 새 버전을 만든다");
		}
		return false;
	}

}
