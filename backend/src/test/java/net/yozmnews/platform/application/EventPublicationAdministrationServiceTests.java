package net.yozmnews.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.yozmnews.platform.BatchResult;
import net.yozmnews.platform.DiscardOutcome;
import net.yozmnews.platform.DiscardResult;
import net.yozmnews.platform.domain.EventResubmissionPolicy;
import net.yozmnews.platform.domain.RegistryRows;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

/**
 * 폐기 — 읽은 뒤 행이 바뀌어 지우지 못한 경우. 실제 레지스트리에서는 읽기와 지우기 사이에 끼어들 수 없어 대역으로 본다.
 * <p>
 * 지키는 규칙: platform-R15
 */
class EventPublicationAdministrationServiceTests {

	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000033");

	private final EventResubmissionPolicy policy = new EventResubmissionPolicy(Duration.ofMinutes(1),
			Duration.ofMinutes(30));

	@Test
	void reportsChangedWhenTheRowChangedAfterReading() {
		assertThat(serviceDeleting(false).discard(Set.of(ID)))
			.isEqualTo(new BatchResult.Processed<>(List.of(new DiscardResult(ID, DiscardOutcome.CHANGED))));
	}

	@Test
	void reportsDiscardedWhenTheRowWasUnchanged() {
		assertThat(serviceDeleting(true).discard(Set.of(ID)))
			.isEqualTo(new BatchResult.Processed<>(List.of(new DiscardResult(ID, DiscardOutcome.DISCARDED))));
	}

	private EventPublicationAdministrationService serviceDeleting(boolean deleted) {
		RegistryRows.RegistryRow exhausted = new RegistryRows.RegistryRow(ID, "net.yozmnews.Probe", "listener",
				Instant.parse("2026-10-01T00:00:00Z"), Status.FAILED, 6, null, true);
		return new EventPublicationAdministrationService(new NoIncompletePublications(), this.policy,
				new OneRow(exhausted, deleted));
	}

	private record OneRow(RegistryRows.RegistryRow row, boolean deleted) implements RegistryRows {

		@Override
		public List<RegistryRow> findAll() {
			return List.of(this.row);
		}

		@Override
		public List<RegistryRow> findByIds(Collection<UUID> ids) {
			return ids.contains(this.row.id()) ? List.of(this.row) : List.of();
		}

		@Override
		public boolean deleteIfUnchanged(RegistryRow row) {
			return this.deleted;
		}

	}

	private static final class NoIncompletePublications implements IncompleteEventPublications {

		@Override
		public void resubmitIncompletePublications(Predicate<EventPublication> filter) {
		}

		@Override
		public void resubmitIncompletePublicationsOlderThan(Duration duration) {
		}

		@Override
		public void resubmitIncompletePublications(ResubmissionOptions options) {
		}

	}

}
