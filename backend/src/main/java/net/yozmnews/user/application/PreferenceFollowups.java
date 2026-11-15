package net.yozmnews.user.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagKeyLineage;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetView;
import net.yozmnews.user.domain.interest.InterestSettings;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import net.yozmnews.user.domain.interest.PreferenceVersionFence;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.PreferencesAdjustedContent;
import net.yozmnews.user.domain.preference.Adjustment;
import net.yozmnews.user.domain.preference.FollowResult;
import net.yozmnews.user.domain.preference.PreferenceAction;
import net.yozmnews.user.domain.preference.PreferenceCause;
import net.yozmnews.user.domain.preference.PreferenceChange;
import net.yozmnews.user.domain.preference.PreferenceFollowup;
import net.yozmnews.user.domain.preference.PreferenceSet;
import net.yozmnews.user.domain.preference.TagPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 따라가기 — 선호 버전이 지금 활성 버전과 다른 사용자의 선호를 계보대로 활성 버전의 키로 고쳐 쓴다 — docs/specs/user.md §4 · user-R47 ·
 * user-R48
 * <p>
 * 사용자 하나를 고쳐 쓰는 정본은 {@link #followLocked} 하나다 — 리스너({@link #followAll})와 관심 설정의 조회 · 저장 유스케이스가 함께 쓴다.
 * 해제는 옛 키의 뺌(옛 버전), 더함은 활성 키의 더함(활성 버전)으로 이력에 남기고 원인은 {@code TAG_SET_CHANGE} 다. 일대일 이어짐 · 정규화는 이력이
 * 없다. 조정이 있으면 그 사용자에게 선호 조정 알림 하나를 남긴다.
 */
@Service
class PreferenceFollowups {

	private static final Logger logger = LoggerFactory.getLogger(PreferenceFollowups.class);

	/** 한 번에 읽는 사용자 수 */
	static final int BATCH = 100;

	private final InterestSettingsRepository settings;

	private final TagPreferenceRepository preferences;

	private final NoticeRepository notices;

	private final PreferenceVersionFence fence;

	private final TagSetDirectory tagSets;

	private final TagKeyLineage lineage;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	PreferenceFollowups(InterestSettingsRepository settings, TagPreferenceRepository preferences,
			NoticeRepository notices, PreferenceVersionFence fence, TagSetDirectory tagSets, TagKeyLineage lineage,
			TransactionTemplate transaction) {
		this(settings, preferences, notices, fence, tagSets, lineage, transaction, InstantSource.system());
	}

	PreferenceFollowups(InterestSettingsRepository settings, TagPreferenceRepository preferences,
			NoticeRepository notices, PreferenceVersionFence fence, TagSetDirectory tagSets, TagKeyLineage lineage,
			TransactionTemplate transaction, InstantSource clock) {
		this.settings = Objects.requireNonNull(settings, "settings");
		this.preferences = Objects.requireNonNull(preferences, "preferences");
		this.notices = Objects.requireNonNull(notices, "notices");
		this.fence = Objects.requireNonNull(fence, "fence");
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
		this.lineage = Objects.requireNonNull(lineage, "lineage");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 잠근 설정 행의 사용자를 지금 활성 버전으로 따라가게 한다 — 부르는 쪽의 트랜잭션 안에서만. 선호 버전이 활성 버전과 같으면 아무것도 하지 않는다.
	 * @return 따라간 뒤의 설정 행 — 따라가지 않았으면 받은 그대로
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	InterestSettings followLocked(InterestSettings locked, Instant now) {
		return followLocked(locked, now, new Versions());
	}

	/**
	 * 선호 버전이 지금 활성 버전과 다른 사용자를 모두 따라가게 한다 — 트랜잭션 밖에서 부른다. 먼저 울타리를 지나 진행 중인 설정 행 만들기를 기다린다.
	 * 사용자마다 트랜잭션 하나라 한 사용자의 실패는 그 사용자만 되돌리고 예외는 그대로 올린다(이벤트 재처리가 남은 사용자를 고친다).
	 * @return 따라가게 한 사용자 수
	 */
	int followAll() {
		this.fence.passExclusive();
		TagSetId active = this.tagSets.activeTagSet().id();
		Versions versions = new Versions();
		int followed = 0;
		long after = 0;
		while (true) {
			List<UserId> batch = this.settings.withPreferenceVersionOtherThan(active, after, BATCH);
			if (batch.isEmpty()) {
				break;
			}
			for (UserId user : batch) {
				Boolean changed = this.transaction.execute(status -> this.settings.lockByUser(user)
					.map(locked -> followLocked(locked, this.clock.instant(), versions) != locked)
					.orElse(false));
				if (Boolean.TRUE.equals(changed)) {
					followed++;
				}
				after = user.value();
			}
		}
		if (followed > 0) {
			logger.info("태그 체계 버전 {}로 사용자 {}명의 선호를 따라가게 했다", active.value(), followed);
		}
		return followed;
	}

	private InterestSettings followLocked(InterestSettings locked, Instant now, Versions versions) {
		TagSetId active = this.tagSets.activeTagSet().id();
		TagSetId from = locked.preferenceVersion();
		if (from.equals(active)) {
			return locked;
		}
		TagKeyMapping mapping = versions.mapping(from);
		TagSetView fromView = versions.view(from);
		TagSetView toView = versions.view(mapping.to());
		UserId user = locked.user();
		PreferenceSet current = this.preferences.load(user);
		FollowResult result = PreferenceFollowup.follow(current, fromView, toView, mapping);
		this.preferences.replace(user, result.next(), now);
		List<PreferenceChange> removed = new ArrayList<>();
		List<PreferenceChange> added = new ArrayList<>();
		for (Adjustment adjustment : result.adjustments()) {
			if (adjustment.released()) {
				removed.add(new PreferenceChange(adjustment.kind(), adjustment.key(), PreferenceAction.REMOVE));
			}
			else {
				added.add(new PreferenceChange(adjustment.kind(), adjustment.key(), PreferenceAction.ADD));
			}
		}
		this.preferences.record(user, removed, PreferenceCause.TAG_SET_CHANGE, from, now);
		this.preferences.record(user, added, PreferenceCause.TAG_SET_CHANGE, mapping.to(), now);
		InterestSettings followed = locked.withPreferenceVersion(mapping.to(), now);
		this.settings.update(followed);
		if (!result.adjustments().isEmpty()) {
			this.notices.create(user, new PreferencesAdjustedContent(toView.number(), result.adjustments()), now);
		}
		return this.settings.lockByUser(user).orElseThrow();
	}

	/** 호출 하나 안에서 버전마다 한 번만 읽는다 — 버전 내용은 바뀌지 않는다 */
	private final class Versions {

		private final Map<TagSetId, TagSetView> views = new HashMap<>();

		private final Map<TagSetId, TagKeyMapping> mappings = new HashMap<>();

		TagSetView view(TagSetId id) {
			return this.views.computeIfAbsent(id, tagSets::tagSet);
		}

		TagKeyMapping mapping(TagSetId from) {
			TagKeyMapping cached = this.mappings.get(from);
			if (cached != null && cached.to().equals(tagSets.activeTagSet().id())) {
				return cached;
			}
			TagKeyMapping mapping = lineage.toActive(from);
			this.mappings.put(from, mapping);
			return mapping;
		}

	}

}
