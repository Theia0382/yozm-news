package net.yozmnews.user.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagRef;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetView;
import net.yozmnews.user.domain.interest.InterestSettings;
import net.yozmnews.user.domain.preference.PreferenceCause;
import net.yozmnews.user.domain.preference.PreferenceSet;
import net.yozmnews.user.domain.preference.TagPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 태그 선호 조회 · 저장 — docs/specs/user.md §4 · user-R45 · user-R46
 * <p>
 * 둘 다 설정 행을 잠그고 먼저 따라간 뒤 한다. 저장은 화면이 본 체계 버전이 지금 활성 버전과 다르면 바꾸지 않는다. 같으면 요청을 정규화해 통째로 바꾸고
 * 차이를 이력(원인 {@code USER})으로 남긴다.
 */
@Service
public class TagPreferences {

	private final InterestSettingsLock lock;

	private final TagPreferenceRepository preferences;

	private final TagSetDirectory tagSets;

	TagPreferences(InterestSettingsLock lock, TagPreferenceRepository preferences, TagSetDirectory tagSets) {
		this.lock = Objects.requireNonNull(lock, "lock");
		this.preferences = Objects.requireNonNull(preferences, "preferences");
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
	}

	/** 활성 체계와 세션 사용자의 선호 */
	@Transactional
	public TagPreferenceView view(UserId user, Instant now) {
		InterestSettings locked = this.lock.lockFollowed(user, now);
		return new TagPreferenceView(this.tagSets.tagSet(locked.preferenceVersion()), this.preferences.load(user));
	}

	/**
	 * @param seen 화면이 본 체계 버전
	 * @throws UnknownTagKeysException 요청에 그 버전에 없는 키가 있을 때 — 아무것도 바꾸지 않는다
	 */
	@Transactional
	public TagPreferenceSaveOutcome save(UserId user, TagSetId seen, PreferenceSet requested, Instant now) {
		InterestSettings locked = this.lock.lockFollowed(user, now);
		TagSetId active = locked.preferenceVersion();
		if (!active.equals(seen)) {
			return new TagPreferenceSaveOutcome.TagSetChanged(active);
		}
		TagSetView version = this.tagSets.tagSet(active);
		List<TagRef> unknown = requested.unknownKeys(version);
		if (!unknown.isEmpty()) {
			throw new UnknownTagKeysException(unknown);
		}
		PreferenceSet next = requested.normalized(version);
		this.preferences.apply(user, this.preferences.load(user).changesTo(next), PreferenceCause.USER, active, now);
		return new TagPreferenceSaveOutcome.Saved(next);
	}

	/** 활성 체계와 선호 */
	public record TagPreferenceView(TagSetView tagSet, PreferenceSet preferences) {
	}

	/** 태그 선호 저장의 결과 */
	public sealed interface TagPreferenceSaveOutcome {

		/** 저장했다 — 정규화한 선호 */
		record Saved(PreferenceSet preferences) implements TagPreferenceSaveOutcome {
		}

		/** 화면이 본 체계 버전이 지금 활성 버전이 아니다 — 바꾸지 않았다 */
		record TagSetChanged(TagSetId active) implements TagPreferenceSaveOutcome {
		}

	}

	/** 요청에 그 버전에 없는 키가 있다 — 요청 형식의 문제다 */
	public static final class UnknownTagKeysException extends RuntimeException {

		private final List<TagRef> keys;

		UnknownTagKeysException(List<TagRef> keys) {
			super("체계에 없는 태그 키: " + keys);
			this.keys = List.copyOf(keys);
		}

		public List<TagRef> keys() {
			return this.keys;
		}

	}

}
