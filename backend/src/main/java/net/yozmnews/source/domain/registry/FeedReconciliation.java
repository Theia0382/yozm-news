package net.yozmnews.source.domain.registry;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.FeedSpec;

/**
 * 프로필의 피드와 저장된 피드 행을 견준 결과 — 배포로 프리셋이 바뀐 뒤 기동할 때 쓴다. 저장된 행은 지우지 않는다: 피드 식별자는 그
 * 피드에서 받은 기사의 기록이 가리킨다.
 *
 * @param missing 프로필에 있고 행이 없는 피드 — 프로필 순서
 * @param orphaned 행은 있고 프로필에 없는 피드 — 받은 순서
 */
public record FeedReconciliation(List<FeedKey> missing, List<FeedKey> orphaned) {

	public FeedReconciliation {
		missing = List.copyOf(missing);
		orphaned = List.copyOf(orphaned);
	}

	public static FeedReconciliation compare(CollectionProfile profile, Collection<FeedKey> stored) {
		Set<FeedKey> inProfile = new LinkedHashSet<>(profile.feeds().stream().map(FeedSpec::key).toList());
		Set<FeedKey> inStore = new LinkedHashSet<>(stored);
		return new FeedReconciliation(inProfile.stream().filter(key -> !inStore.contains(key)).toList(),
				inStore.stream().filter(key -> !inProfile.contains(key)).toList());
	}

}
