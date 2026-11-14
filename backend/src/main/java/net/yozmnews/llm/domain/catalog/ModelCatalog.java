package net.yozmnews.llm.domain.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;

/**
 * 검증된 허용 모델 목록 — docs/specs/llm.md §4 · llm-R2. 불변이다
 */
public final class ModelCatalog {

	private final List<LlmModel> models;

	/**
	 * @param models 형식 규칙을 통과한 모델 — {@link ModelCatalogParser} 가 만든다
	 */
	ModelCatalog(List<LlmModel> models) {
		List<LlmModel> ordered = new ArrayList<>(models.size());
		for (Provider provider : Provider.values()) {
			for (LlmModel model : models) {
				if (model.provider() == provider) {
					ordered.add(model);
				}
			}
		}
		this.models = List.copyOf(ordered);
	}

	/** 공급자 선언 순, 공급자 안에서는 파일에 적힌 순 */
	public List<LlmModel> all() {
		return this.models;
	}

	/** 그 공급자의 모델 — 없으면 빈 목록 */
	public List<LlmModel> of(Provider provider) {
		Objects.requireNonNull(provider, "provider");
		return this.models.stream().filter(model -> model.provider() == provider).toList();
	}

	/** 목록에 없는 모델이면 빈 값 */
	public Optional<LlmModel> find(Provider provider, String id) {
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(id, "id");
		return this.models.stream().filter(model -> model.provider() == provider && model.id().equals(id)).findFirst();
	}

}
