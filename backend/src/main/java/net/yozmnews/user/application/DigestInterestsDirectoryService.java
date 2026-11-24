package net.yozmnews.user.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.account.AccountDirectory;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetView;
import net.yozmnews.user.DigestInterests;
import net.yozmnews.user.DigestInterestsDirectory;
import net.yozmnews.user.TopicSubscription;
import net.yozmnews.user.application.PreferenceFollowups.Followed;
import net.yozmnews.user.domain.interest.DigestVolume;
import net.yozmnews.user.domain.interest.ExcludedSourceRepository;
import net.yozmnews.user.domain.interest.InterestSettings;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import net.yozmnews.user.domain.preference.PreferenceSet;
import net.yozmnews.user.domain.preference.TagPreferenceRepository;
import net.yozmnews.user.domain.preference.TopicExpansion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DigestInterestsDirectory} 의 구현 — 쓰지 않는다. docs/specs/user.md §4
 * <p>
 * 설정 행을 잠그지 않으므로 설정 행 · 선호 행 · 활성 체계 · 계보를 한 스냅숏에서 읽어야 한다 — 읽기 전용 {@code REPEATABLE_READ} 트랜잭션
 * 하나다. 기본 격리({@code READ COMMITTED})면 설정 행을 읽은 뒤 리스너의 따라가기가 커밋되어 옛 선호 버전과 새 버전 키의 선호를 섞어 읽을 수 있고,
 * 따라가기 계산이 그 버전에 없는 키로 실패한다. PostgreSQL 의 읽기 전용 {@code REPEATABLE_READ} 는 직렬화 실패 없이 한 스냅숏을 준다.
 */
@Service
class DigestInterestsDirectoryService implements DigestInterestsDirectory {

	private final AccountDirectory accounts;

	private final InterestSettingsRepository settings;

	private final TagPreferenceRepository preferences;

	private final ExcludedSourceRepository excludedSources;

	private final TagSetDirectory tagSets;

	private final PreferenceFollowups followups;

	DigestInterestsDirectoryService(AccountDirectory accounts, InterestSettingsRepository settings,
			TagPreferenceRepository preferences, ExcludedSourceRepository excludedSources, TagSetDirectory tagSets,
			PreferenceFollowups followups) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.preferences = Objects.requireNonNull(preferences, "preferences");
		this.excludedSources = Objects.requireNonNull(excludedSources, "excludedSources");
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
		this.followups = Objects.requireNonNull(followups, "followups");
	}

	@Override
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public Optional<DigestInterests> interestsOf(UserId user) {
		Objects.requireNonNull(user, "user");
		if (!this.accounts.exists(user)) {
			return Optional.empty();
		}
		TagSetView active = this.tagSets.activeTagSet();
		Optional<InterestSettings> row = this.settings.findByUser(user);
		// 설정 행이 없으면 계정을 만들 때의 값 — InterestDefaultsWriter 와 같은 정본. 고친 시각은 읽지 않는다
		InterestSettings stored = row.orElseGet(() -> InterestSettings.defaults(user, active.id(), Instant.EPOCH));
		PreferenceSet current = row.isPresent() ? this.preferences.load(user) : PreferenceSet.onboarding(active);
		TagSetId version = stored.preferenceVersion();
		TagSetView view = active;
		if (!version.equals(active.id())) {
			Followed followed = this.followups.followed(version, current);
			current = followed.result().next();
			version = followed.version();
			view = followed.view();
		}
		List<TopicSubscription> topics = TopicExpansion.expand(current.topics(), view)
			.entrySet()
			.stream()
			.map(entry -> new TopicSubscription(entry.getKey(), entry.getValue()))
			.toList();
		DigestVolume volume = stored.volume();
		return Optional.of(new DigestInterests(version, topics, current.excludedRegions(), current.excludedFormats(),
				this.excludedSources.load(user), stored.interestText().value(), stored.schedule().timezone(),
				volume.maxCandidates(), volume.bigSlots(), volume.summaryLength(), stored.effort()));
	}

}
