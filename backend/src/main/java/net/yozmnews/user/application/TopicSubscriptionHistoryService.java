package net.yozmnews.user.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.account.AccountDirectory;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagKeyLineage;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetView;
import net.yozmnews.user.TopicSubscriptionAction;
import net.yozmnews.user.TopicSubscriptionCause;
import net.yozmnews.user.TopicSubscriptionChange;
import net.yozmnews.user.TopicSubscriptionChanges;
import net.yozmnews.user.TopicSubscriptionHistory;
import net.yozmnews.user.domain.preference.PreferenceAction;
import net.yozmnews.user.domain.preference.PreferenceCause;
import net.yozmnews.user.domain.preference.RecordedTopicChange;
import net.yozmnews.user.domain.preference.TagPreferenceRepository;
import net.yozmnews.user.domain.preference.TopicExpansion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link TopicSubscriptionHistory} 의 구현 — 선호 이력의 주제 행을 기록 당시 버전의 구조로 펼치고(대분류는 하위 중분류 전체 —
 * {@link TopicExpansion}) 계보 표로 활성 버전의 중분류로 푼다. 쓰지 않는다. docs/specs/user.md §4 · user-R58
 * <p>
 * 읽기 전용 {@code REPEATABLE_READ} 트랜잭션 하나에서 이력 행 · 활성 체계 · 옛 버전의 구조 · 계보 표를 함께 읽는다 —
 * {@code TagSetDirectory} · {@code TagKeyLineage} 는 트랜잭션을 따로 열지 않아 같은 스냅숏이다. 그래서 계보 표의 기준 버전은 언제나 읽은
 * 활성 버전이고, 다르면 불변식 위반이다. 조회가 끝난 뒤의 활성 전환은 부르는 쪽이 낸 {@link TopicSubscriptionChanges#tagSet()} 으로
 * 알아챈다.
 */
@Service
class TopicSubscriptionHistoryService implements TopicSubscriptionHistory {

	private final AccountDirectory accounts;

	private final TagPreferenceRepository preferences;

	private final TagSetDirectory tagSets;

	private final TagKeyLineage lineage;

	TopicSubscriptionHistoryService(AccountDirectory accounts, TagPreferenceRepository preferences,
			TagSetDirectory tagSets, TagKeyLineage lineage) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.preferences = Objects.requireNonNull(preferences, "preferences");
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
		this.lineage = Objects.requireNonNull(lineage, "lineage");
	}

	@Override
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public TopicSubscriptionChanges changes(UserId user, Instant from, Instant to) {
		Objects.requireNonNull(user, "user");
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("기간의 시작이 끝보다 늦다: " + from + " · " + to);
		}
		TagSetId active = this.tagSets.activeTagSet().id();
		if (!this.accounts.exists(user)) {
			return new TopicSubscriptionChanges(active, List.of());
		}
		Map<TagSetId, TagSetView> views = new HashMap<>();
		Map<TagSetId, TagKeyMapping> mappings = new HashMap<>();
		List<TopicSubscriptionChange> changes = this.preferences.topicChanges(user, from, to).stream().map(row -> {
			TagSetView view = views.computeIfAbsent(row.version(), this.tagSets::tagSet);
			TagKeyMapping mapping = mappings.computeIfAbsent(row.version(), this.lineage::toActive);
			if (!mapping.to().equals(active)) {
				throw new IllegalStateException(
						"한 스냅숏의 계보 표가 활성 버전 " + active.value() + " 이 아닌 " + mapping.to().value() + " 을 가리킨다");
			}
			return new TopicSubscriptionChange(row.key(), row.version(), resolve(row, view, mapping),
					action(row.action()), cause(row.cause()), row.occurredAt());
		}).toList();
		return new TopicSubscriptionChanges(active, changes);
	}

	/** 그 행이 덮던 중분류를 활성 버전의 중분류로 — 계보 없이 지운 것은 빠진다 */
	private static Set<String> resolve(RecordedTopicChange row, TagSetView view, TagKeyMapping mapping) {
		Set<String> resolved = new HashSet<>();
		for (String subtopic : TopicExpansion.expand(List.of(row.key()), view).get(row.key())) {
			resolved.addAll(mapping.resolve(TagKind.TOPIC, subtopic));
		}
		return resolved;
	}

	private static TopicSubscriptionAction action(PreferenceAction action) {
		return switch (action) {
			case ADD -> TopicSubscriptionAction.ADD;
			case REMOVE -> TopicSubscriptionAction.REMOVE;
		};
	}

	private static TopicSubscriptionCause cause(PreferenceCause cause) {
		return switch (cause) {
			case USER -> TopicSubscriptionCause.USER;
			case ONBOARDING -> TopicSubscriptionCause.ONBOARDING;
			case TAG_SET_CHANGE -> TopicSubscriptionCause.TAG_SET_CHANGE;
		};
	}

}
