package net.yozmnews.user.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.UserStorylineDirectory;
import net.yozmnews.user.UserStorylineId;
import net.yozmnews.user.UserStorylineView;
import net.yozmnews.user.domain.userstoryline.UserStorylineReconcile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UserStorylineDirectory} 의 구현 — 저장 행과 창 안의 신호 · 지금 관계로 맞추기 결과를 계산해 낸다. 쓰지 않는다 — 판정은 저장하는
 * 따라가기({@link UserStorylineFollowups})와 같은 {@link UserStorylineMaterials} 가 낸다. docs/specs/user.md §4
 */
@Service
class UserStorylineDirectoryService implements UserStorylineDirectory {

	private final UserStorylineMaterials materials;

	UserStorylineDirectoryService(UserStorylineMaterials materials) {
		this.materials = Objects.requireNonNull(materials, "materials");
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserStorylineView> of(UserId user, Instant from, Instant to) {
		Objects.requireNonNull(user, "user");
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("창의 시작이 끝보다 늦다: " + from + " · " + to);
		}
		UserStorylineReconcile result = this.materials.match(user, from, to).result();
		List<UserStorylineView> views = new ArrayList<>();
		result.kept()
			.forEach(kept -> views
				.add(new UserStorylineView(new UserStorylineId(kept.id()), tops(kept.tops()), kept.subscribedAt() != null)));
		result.created()
			.forEach(created -> views
				.add(new UserStorylineView(null, tops(created.tops()), created.subscribedAt() != null)));
		views.sort(Comparator.comparingLong(view -> new TreeSet<>(
				view.tops().stream().map(StorylineId::value).collect(Collectors.toSet()))
			.first()));
		return List.copyOf(views);
	}

	private static Set<StorylineId> tops(Set<Long> tops) {
		return tops.stream().map(StorylineId::new).collect(Collectors.toUnmodifiableSet());
	}

}
