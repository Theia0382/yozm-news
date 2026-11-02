package net.yozmnews.source.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;

import net.yozmnews.platform.WorkerRole;
import net.yozmnews.source.domain.attribution.SourceAttributionRepository;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.registry.Feed;
import net.yozmnews.source.domain.registry.FeedReconciliation;
import net.yozmnews.source.domain.registry.FeedRepository;
import net.yozmnews.source.domain.registry.ProfileResolution;
import net.yozmnews.source.domain.registry.ProfileResolver;
import net.yozmnews.source.domain.registry.Source;
import net.yozmnews.source.domain.registry.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기동 대조 — docs/specs/source.md §4 · source-R8.
 * <p>
 * 프로필을 얻은 소스는 같은 트랜잭션에서 프로필의 소스 이름을 출처 이름으로 기록한다 — 이미 있던 소스의 기록도 여기서 채워진다.
 */
@WorkerRole
@Component
class PresetFeedReconciliation {

	private static final Logger logger = LoggerFactory.getLogger(PresetFeedReconciliation.class);

	private final SourceRepository sources;

	private final FeedRepository feeds;

	private final ProfileResolver resolver;

	private final FeedProvisioner provisioner;

	private final SourceAttributionRepository attributions;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	PresetFeedReconciliation(SourceRepository sources, FeedRepository feeds, ProfileResolver resolver,
			FeedProvisioner provisioner, SourceAttributionRepository attributions, TransactionTemplate transaction) {
		this(sources, feeds, resolver, provisioner, attributions, transaction, InstantSource.system());
	}

	PresetFeedReconciliation(SourceRepository sources, FeedRepository feeds, ProfileResolver resolver,
			FeedProvisioner provisioner, SourceAttributionRepository attributions, TransactionTemplate transaction,
			InstantSource clock) {
		this.sources = sources;
		this.feeds = feeds;
		this.resolver = resolver;
		this.provisioner = provisioner;
		this.attributions = attributions;
		this.transaction = transaction;
		this.clock = clock;
	}

	@EventListener(ApplicationReadyEvent.class)
	void reconcile() {
		for (Source source : this.sources.findAll()) {
			// 숨긴 소스도 대조한다 — 다시 보일 때 숨긴 동안 생긴 피드가 이미 있게 한다. 숨긴 소스의 경고는 debug 로 낮춘다
			Level level = source.hidden() ? Level.DEBUG : Level.WARN;
			switch (this.resolver.resolve(source)) {
				case ProfileResolution.Resolved resolved -> reconcileApart(source, resolved);
				case ProfileResolution.PresetMissing missing -> logger.atLevel(level)
					.log("프리셋이 사라졌다 — 소스 {} 의 피드를 수집하지 않는다: {}", source.id().value(), missing.key());
				case ProfileResolution.Invalid invalid -> logger.atLevel(level)
					.log("운영자 프로필이 검증에 걸린다 — 소스 {} 의 피드를 수집하지 않는다: {}", source.id().value(), invalid.problems());
			}
		}
	}

	/**
	 * 소스 하나를 대조한다 — 다른 인스턴스의 대조나 운영자 변경이 같은 피드 행을 먼저 저장했으면 이 소스를 건너뛰고 다음 소스로 간다. 다음
	 * 기동이 다시 대조한다.
	 */
	private void reconcileApart(Source source, ProfileResolution.Resolved resolved) {
		try {
			this.transaction.executeWithoutResult(status -> reconcile(source, resolved));
		}
		catch (OptimisticLockingFailureException ex) {
			logger.warn("다른 변경과 겹쳐 이 소스의 기동 대조를 건너뛴다 — 소스 {}: {}", source.id().value(), ex.getMessage());
		}
	}

	private void reconcile(Source source, ProfileResolution.Resolved resolved) {
		// 목록을 읽은 뒤 지워진 소스는 건너뛴다 — 공유 잠금이 삭제와 줄을 세우고, 지워졌으면 행이 없다
		if (this.sources.lockForKeyShare(source.id().value()).isEmpty()) {
			logger.info("기동 대조 사이 지워진 소스를 건너뛴다 — 소스 {}", source.id().value());
			return;
		}
		List<Feed> stored = this.feeds.findAllBySourceId(source.id().value());
		FeedReconciliation reconciliation = FeedReconciliation.compare(resolved.profile(), stored);
		Instant now = this.clock.instant();
		this.attributions.record(source.id(), resolved.profile().source().name(), now);
		for (FeedKey key : reconciliation.missing()) {
			this.provisioner.provision(source.id(), key, now);
			logger.info("프로필에 새로 생긴 피드를 꺼진 채로 더했다 — 소스 {} 피드 {}", source.id().value(), key);
		}
		for (Feed feed : stored) {
			if (reconciliation.reappeared().contains(feed.feedKey())) {
				feed.unhide();
				this.feeds.save(feed);
				logger.info("프로필에 다시 생긴 숨긴 피드를 꺼진 채로 다시 보인다 — 소스 {} 피드 {}", source.id().value(), feed.feedKey());
			}
		}
		Level level = source.hidden() ? Level.DEBUG : Level.WARN;
		for (FeedKey key : reconciliation.orphaned()) {
			logger.atLevel(level).log("프로필에서 빠진 피드 — 소스 {} 피드 {} 를 수집하지 않는다", source.id().value(), key);
		}
	}

}
