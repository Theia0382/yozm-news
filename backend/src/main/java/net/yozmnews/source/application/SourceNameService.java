package net.yozmnews.source.application;

import java.util.List;

import net.yozmnews.source.SourceName;
import net.yozmnews.source.SourceNames;
import org.springframework.stereotype.Service;

/**
 * 공개 조회 {@link SourceNames} 의 구현 — docs/specs/source.md §4. {@link SourceCatalog#names()} 에 맡긴다 — 이름의 해석은 운영
 * 목록과 같은 함수이고, 피드 · 수집 상태는 읽지 않는다(source-R51 · source-R57).
 */
@Service
class SourceNameService implements SourceNames {

	private final SourceCatalog catalog;

	SourceNameService(SourceCatalog catalog) {
		this.catalog = catalog;
	}

	@Override
	public List<SourceName> visible() {
		return this.catalog.names();
	}

}
