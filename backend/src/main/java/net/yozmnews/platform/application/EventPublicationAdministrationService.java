package net.yozmnews.platform.application;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.yozmnews.platform.BatchResult;
import net.yozmnews.platform.DiscardOutcome;
import net.yozmnews.platform.DiscardResult;
import net.yozmnews.platform.EventPublicationAdministration;
import net.yozmnews.platform.ResubmissionOutcome;
import net.yozmnews.platform.ResubmissionResult;
import net.yozmnews.platform.StrandedPublication;
import net.yozmnews.platform.StrandedPublications;
import net.yozmnews.platform.StrandedReason;
import net.yozmnews.platform.domain.EventResubmissionPolicy;
import net.yozmnews.platform.domain.EventResubmissionPolicy.Decision;
import net.yozmnews.platform.domain.RegistryRows;
import net.yozmnews.platform.domain.RegistryRows.RegistryRow;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.stereotype.Service;

/**
 * 공개 운영 API {@link EventPublicationAdministration} 의 구현 — docs/specs/platform.md §4 · platform-R14 ~ platform-R16.
 * <p>
 * 트랜잭션을 열지 않는다 — 재제출의 상태 갱신은 Spring Modulith 가 하고, 폐기는 행마다 문장 하나다.
 */
@Service
class EventPublicationAdministrationService implements EventPublicationAdministration {

	private static final Logger logger = LoggerFactory.getLogger(EventPublicationAdministrationService.class);

	private final IncompleteEventPublications publications;

	private final EventResubmissionPolicy policy;

	private final RegistryRows rows;

	EventPublicationAdministrationService(IncompleteEventPublications publications, EventResubmissionPolicy policy,
			RegistryRows rows) {
		this.publications = publications;
		this.policy = policy;
		this.rows = rows;
	}

	@Override
	public StrandedPublications strandedPublications() {
		List<StrandedPublication> stranded = this.rows.findAll()
			.stream()
			.flatMap((row) -> strandedReason(row).map((reason) -> publication(row, reason)).stream())
			.toList();
		return new StrandedPublications(stranded.subList(0, Math.min(stranded.size(), MAX_BATCH)), stranded.size());
	}

	@Override
	public BatchResult<ResubmissionResult> resubmit(Set<UUID> ids) {
		if (ids.size() > MAX_BATCH) {
			return new BatchResult.OverLimit<>(MAX_BATCH);
		}
		Map<UUID, RegistryRow> read = byId(this.rows.findByIds(ids));
		Set<UUID> passed = new HashSet<>();
		this.publications.resubmitIncompletePublications((publication) -> ids.contains(publication.getIdentifier())
				&& this.policy.decide(publication) == Decision.EXHAUSTED && passed.add(publication.getIdentifier()));
		return new BatchResult.Processed<>(sorted(ids).stream()
			.map((id) -> new ResubmissionResult(id, resubmissionOutcome(passed.contains(id), read.get(id))))
			.toList());
	}

	@Override
	public BatchResult<DiscardResult> discard(Set<UUID> ids) {
		if (ids.size() > MAX_BATCH) {
			return new BatchResult.OverLimit<>(MAX_BATCH);
		}
		Map<UUID, RegistryRow> read = byId(this.rows.findByIds(ids));
		return new BatchResult.Processed<>(
				sorted(ids).stream().map((id) -> new DiscardResult(id, discard(read.get(id)))).toList());
	}

	private ResubmissionOutcome resubmissionOutcome(boolean resubmitted, @Nullable RegistryRow row) {
		if (resubmitted) {
			return ResubmissionOutcome.RESUBMITTED;
		}
		if (row == null) {
			return ResubmissionOutcome.NOT_FOUND;
		}
		return strandedReason(row).filter((reason) -> reason == StrandedReason.UNREADABLE).isPresent()
				? ResubmissionOutcome.UNREADABLE : ResubmissionOutcome.NOT_EXHAUSTED;
	}

	private DiscardOutcome discard(@Nullable RegistryRow row) {
		if (row == null) {
			return DiscardOutcome.NOT_FOUND;
		}
		Optional<StrandedReason> reason = strandedReason(row);
		if (reason.isEmpty()) {
			return DiscardOutcome.NOT_STRANDED;
		}
		if (!this.rows.deleteIfUnchanged(row)) {
			return DiscardOutcome.CHANGED;
		}
		logger.warn(
				"운영자가 이벤트 발행을 폐기했다: id={}, reason={}, eventType={}, listenerId={}, publishedAt={}, completionAttempts={}",
				row.id(), reason.get(), row.eventType(), row.listenerId(), row.publishedAt(), row.completionAttempts());
		return DiscardOutcome.DISCARDED;
	}

	/** 상태가 비어 있는 행은 판정에 넘기지 않는다 — Spring Modulith 가 쓰는 행에는 상태가 늘 있다 */
	private Optional<StrandedReason> strandedReason(RegistryRow row) {
		EventPublication.Status status = row.status();
		if (status == null) {
			return Optional.empty();
		}
		return this.policy.stranded(status, row.completionAttempts(), row.eventTypeLoadable());
	}

	private static StrandedPublication publication(RegistryRow row, StrandedReason reason) {
		return new StrandedPublication(row.id(), reason, row.eventType(), row.listenerId(), row.publishedAt(),
				row.completionAttempts(), row.lastResubmittedAt());
	}

	private static Map<UUID, RegistryRow> byId(List<RegistryRow> rows) {
		return rows.stream().collect(Collectors.toMap(RegistryRow::id, Function.identity()));
	}

	private static List<UUID> sorted(Set<UUID> ids) {
		return ids.stream().sorted().toList();
	}

}
