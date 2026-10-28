package net.yozmnews.source.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기동 때 소스마다 지금의 프로필과 저장된 피드 행을 대조한다 — 프리셋은 배포로만 바뀌므로 기동이 대조할 때다. 프로필에 새로 생긴
 * 피드는 꺼진 행으로 더하고, 가리킬 곳을 잃은 소스 · 피드는 경고 로그로 알린다. 행은 지우지도 끄지도 않는다 — 운영자 스위치는
 * 운영자의 것이고, 피드 식별자는 받은 기사의 기록이 가리킨다. 여러 인스턴스가 함께 돌아도 결과가 같다.
 */
@Component
class PresetFeedReconciliation {

	private static final Logger logger = LoggerFactory.getLogger(PresetFeedReconciliation.class);

	private final SourceRepository sources;

	private final FeedRepository feeds;

	private final ProfileResolver resolver;

	private final FeedProvisioner provisioner;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	PresetFeedReconciliation(SourceRepository sources, FeedRepository feeds, ProfileResolver resolver,
			FeedProvisioner provisioner, TransactionTemplate transaction) {
		this(sources, feeds, resolver, provisioner, transaction, InstantSource.system());
	}

	PresetFeedReconciliation(SourceRepository sources, FeedRepository feeds, ProfileResolver resolver,
			FeedProvisioner provisioner, TransactionTemplate transaction, InstantSource clock) {
		this.sources = sources;
		this.feeds = feeds;
		this.resolver = resolver;
		this.provisioner = provisioner;
		this.transaction = transaction;
		this.clock = clock;
	}

	@EventListener(ApplicationReadyEvent.class)
	void reconcile() {
		for (Source source : this.sources.findAll()) {
			switch (this.resolver.resolve(source)) {
				case ProfileResolution.Resolved resolved -> this.transaction
					.executeWithoutResult(status -> reconcile(source, resolved));
				case ProfileResolution.PresetMissing missing -> logger
					.warn("프리셋이 사라졌다 — 소스 {} 의 피드를 수집하지 않는다: {}", source.id().value(), missing.key());
				case ProfileResolution.Invalid invalid -> logger.warn(
						"운영자 프로필이 검증에 걸린다 — 소스 {} 의 피드를 수집하지 않는다: {}", source.id().value(), invalid.problems());
			}
		}
	}

	private void reconcile(Source source, ProfileResolution.Resolved resolved) {
		List<FeedKey> stored = this.feeds.findAllBySourceId(source.id().value()).stream().map(Feed::feedKey).toList();
		FeedReconciliation reconciliation = FeedReconciliation.compare(resolved.profile(), stored);
		Instant now = this.clock.instant();
		for (FeedKey key : reconciliation.missing()) {
			this.provisioner.provision(source.id(), key, now);
			logger.info("프로필에 새로 생긴 피드를 꺼진 채로 더했다 — 소스 {} 피드 {}", source.id().value(), key);
		}
		for (FeedKey key : reconciliation.orphaned()) {
			logger.warn("프로필에서 빠진 피드 — 소스 {} 피드 {} 를 수집하지 않는다", source.id().value(), key);
		}
	}

}
