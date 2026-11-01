package net.yozmnews.source.domain.registry;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.FeedSpec;

/**
 * 프로필의 피드와 저장된 피드 행을 견준 결과 — docs/specs/source.md §4 · source-R8.
 *
 * @param missing 프로필에 있고 행이 없는 피드 — 프로필 순서
 * @param orphaned 숨기지 않은 행 가운데 프로필에 없는 피드 — 받은 순서. 숨긴 피드는 정의상 프로필에 없으므로 들지 않는다
 * @param reappeared 숨긴 행 가운데 프로필에 다시 생긴 피드 — 받은 순서. 숨긴 시각을 지워 다시 보인다
 */
public record FeedReconciliation(List<FeedKey> missing, List<FeedKey> orphaned, List<FeedKey> reappeared) {

	public FeedReconciliation {
		missing = List.copyOf(missing);
		orphaned = List.copyOf(orphaned);
		reappeared = List.copyOf(reappeared);
	}

	/** 저장된 피드 행을 숨김 여부로 나눠 견준다 */
	public static FeedReconciliation compare(CollectionProfile profile, List<Feed> stored) {
		return compare(profile, stored.stream().filter(feed -> !feed.hidden()).map(Feed::feedKey).toList(),
				stored.stream().filter(Feed::hidden).map(Feed::feedKey).toList());
	}

	/**
	 * @param visible 숨기지 않은 피드 행의 식별자
	 * @param hidden 숨긴 피드 행의 식별자
	 */
	public static FeedReconciliation compare(CollectionProfile profile, Collection<FeedKey> visible,
			Collection<FeedKey> hidden) {
		Set<FeedKey> inProfile = new LinkedHashSet<>(profile.feeds().stream().map(FeedSpec::key).toList());
		Set<FeedKey> shown = new LinkedHashSet<>(visible);
		Set<FeedKey> hiddenKeys = new LinkedHashSet<>(hidden);
		return new FeedReconciliation(
				inProfile.stream().filter(key -> !shown.contains(key) && !hiddenKeys.contains(key)).toList(),
				shown.stream().filter(key -> !inProfile.contains(key)).toList(),
				hiddenKeys.stream().filter(inProfile::contains).toList());
	}

}
