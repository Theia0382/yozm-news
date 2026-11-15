package net.yozmnews.user.application;

import net.yozmnews.source.SourceDeleted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link SourceDeleted} 리스너 — 그 소스의 제외 행을 지운다. docs/specs/user.md §4 · user-R49
 * <p>
 * 역할 애너테이션이 없어 두 역할 모두에서 켜진다 — 소스 삭제는 화면 역할의 운영자 요청이 낸다. 다시 처리해도 같다.
 */
@Component
class SourceDeletedListener {

	private final ExcludedSources excludedSources;

	SourceDeletedListener(ExcludedSources excludedSources) {
		this.excludedSources = excludedSources;
	}

	@ApplicationModuleListener
	public void on(SourceDeleted deleted) {
		this.excludedSources.forget(deleted.sourceId());
	}

}
