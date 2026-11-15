package net.yozmnews.user.application;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.platform.UserId;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.SourceName;
import net.yozmnews.source.SourceNames;
import net.yozmnews.user.domain.interest.ExcludedSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제외 소스 조회 · 저장 · 지운 소스 잊기 — docs/specs/user.md §4 · user-R49
 * <p>
 * 고를 소스는 {@code source} 의 숨기지 않은 소스다. 저장은 고를 소스 안에서만 바꾼다 — 숨긴 소스의 제외는 남고 고를 소스 밖의 요청은 버린다. 이력이
 * 없다(관심 신호가 아니다).
 */
@Service
public class ExcludedSources {

	private final InterestSettingsLock lock;

	private final ExcludedSourceRepository excluded;

	private final SourceNames sources;

	ExcludedSources(InterestSettingsLock lock, ExcludedSourceRepository excluded, SourceNames sources) {
		this.lock = Objects.requireNonNull(lock, "lock");
		this.excluded = Objects.requireNonNull(excluded, "excluded");
		this.sources = Objects.requireNonNull(sources, "sources");
	}

	/** 고를 소스와 그 가운데 제외한 것 */
	@Transactional
	public ExcludedSourcesView view(UserId user, Instant now) {
		this.lock.lockFollowed(user, now);
		List<SourceName> visible = this.sources.visible();
		return new ExcludedSourcesView(visible, within(this.excluded.load(user), visible));
	}

	/** 고를 소스 안에서 통째로 바꾼다 — 저장한 뒤의 고를 소스 안 제외 */
	@Transactional
	public Set<SourceId> save(UserId user, Set<SourceId> requested, Instant now) {
		this.lock.lockFollowed(user, now);
		List<SourceName> visible = this.sources.visible();
		Set<SourceId> visibleIds = ids(visible);
		Set<SourceId> next = new HashSet<>(this.excluded.load(user));
		next.removeAll(visibleIds);
		requested.stream().filter(visibleIds::contains).forEach(next::add);
		this.excluded.replace(user, next, now);
		return within(next, visible);
	}

	/** 지운 소스의 제외 행을 모두 지운다 — 다시 처리해도 같다 */
	@Transactional
	public int forget(SourceId source) {
		return this.excluded.forget(source);
	}

	private static Set<SourceId> within(Set<SourceId> excluded, List<SourceName> visible) {
		Set<SourceId> ids = ids(visible);
		return excluded.stream().filter(ids::contains).collect(Collectors.toUnmodifiableSet());
	}

	private static Set<SourceId> ids(List<SourceName> visible) {
		return visible.stream().map(SourceName::id).collect(Collectors.toSet());
	}

	/** 고를 소스와 그 가운데 제외한 것 */
	public record ExcludedSourcesView(List<SourceName> sources, Set<SourceId> excluded) {
	}

}
