package net.yozmnews.llm.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.LlmModels;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.domain.catalog.ModelCatalog;
import org.springframework.stereotype.Service;

/**
 * 공개 API {@link LlmModels} 의 구현 — docs/specs/llm.md §4. 기동 때 읽은 목록을 그대로 낸다. 역할 애너테이션이 없다(두 역할 공통)
 */
@Service
class LlmModelsService implements LlmModels {

	private final ModelCatalog catalog;

	LlmModelsService(ModelCatalog catalog) {
		this.catalog = Objects.requireNonNull(catalog, "catalog");
	}

	@Override
	public List<LlmModel> all() {
		return this.catalog.all();
	}

	@Override
	public List<LlmModel> of(Provider provider) {
		return this.catalog.of(provider);
	}

	@Override
	public Optional<LlmModel> find(Provider provider, String id) {
		return this.catalog.find(provider, id);
	}

}
