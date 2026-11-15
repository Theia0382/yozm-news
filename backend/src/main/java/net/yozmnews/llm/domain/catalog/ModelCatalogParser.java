package net.yozmnews.llm.domain.catalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.StrictYamlBinding;
import net.yozmnews.platform.StrictYamlBinding.Located;
import net.yozmnews.platform.TextLocation;
import net.yozmnews.platform.YamlPath;
import net.yozmnews.platform.YamlProblem;
import net.yozmnews.platform.YamlRead;
import net.yozmnews.platform.YamlTextReader;
import net.yozmnews.platform.YamlTree;
import net.yozmnews.platform.YamlTree.ListNode;
import net.yozmnews.platform.YamlTree.MapNode;

/**
 * 허용 모델 목록 파일 형식의 정본 — docs/specs/llm.md §4 · llm-R2.
 * <p>
 * YAML 읽기와 공통 거부는 {@code platform} 이 하고, 이 클래스는 형식(키 · 값의 범위 · 유일성 · 전체 수)을 판정한다. 문제는 원문 순서대로
 * 모두 모은다. 스레드 안전하다.
 */
public final class ModelCatalogParser {

	/** 원문 길이 상한(문자 수) */
	public static final int MAX_CHARS = 65_536;

	/** 모델 ID 의 최대 길이 */
	private static final int MAX_ID_LENGTH = 128;

	/** 입력 토큰 예산의 하한 */
	private static final int MIN_BUDGET = 1;

	/** 입력 토큰 예산의 상한 */
	private static final int MAX_BUDGET = 10_000_000;

	/** 모델 ID 에 쓸 수 있는 문자의 처음 — 출력 가능한 ASCII */
	private static final int FIRST_ID_CHARACTER = 0x21;

	/** 모델 ID 에 쓸 수 있는 문자의 끝 */
	private static final int LAST_ID_CHARACTER = 0x7E;

	private static final String PROVIDERS = "providers";

	private static final String MODELS = "models";

	private static final String ID = "id";

	private static final String BUDGET = "input-token-budget";

	private static final String STRUCTURED = "structured-output";

	private static final Set<String> MODEL_KEYS = Set.of(ID, BUDGET, STRUCTURED);

	private final YamlTextReader reader = new YamlTextReader(MAX_CHARS);

	public ModelCatalogParse parse(String text) {
		Objects.requireNonNull(text, "text");
		return switch (this.reader.read(text)) {
			case YamlRead.Unreadable unreadable -> new ModelCatalogParse.Invalid(List.of(common(unreadable.problem())));
			case YamlRead.Read read -> new Reading().read(read.root());
		};
	}

	private static ModelCatalogProblem common(YamlProblem problem) {
		return new ModelCatalogProblem(problem.path(), problem.message(), problem.location());
	}

	/** 문서 하나를 읽는 동안의 상태 */
	private static final class Reading {

		private final List<ModelCatalogProblem> problems = new ArrayList<>();

		private final StrictYamlBinding binding = new StrictYamlBinding(problem -> this.problems.add(common(problem)));

		private final List<LlmModel> models = new ArrayList<>();

		ModelCatalogParse read(MapNode root) {
			Map<String, YamlTree> top = this.binding.entries(root, YamlPath.ROOT, PROVIDERS::equals);
			YamlPath providersPath = YamlPath.of(PROVIDERS);
			if (!top.containsKey(PROVIDERS)) {
				problem(providersPath, "'" + PROVIDERS + "' 가 없다", root.location());
			}
			MapNode providers = this.binding.map(top.get(PROVIDERS), providersPath);
			if (providers != null) {
				Map<String, YamlTree> entries = this.binding.entries(providers, providersPath,
						key -> Provider.ofKey(key).isPresent(), (keyPath, key, location) -> problem(keyPath,
								"모르는 공급자 '" + key + "' — " + providerKeys() + " 가운데 하나여야 한다", location));
				entries.forEach(
						(key, node) -> provider(Provider.ofKey(key).orElseThrow(), node, providersPath.child(key)));
			}
			if (this.problems.isEmpty() && this.models.isEmpty()) {
				problem(providersPath, "모델이 하나도 없다 — 전체 모델은 하나 이상이어야 한다", root.location());
			}
			if (!this.problems.isEmpty()) {
				return new ModelCatalogParse.Invalid(this.problems);
			}
			return new ModelCatalogParse.Parsed(new ModelCatalog(this.models));
		}

		private void provider(Provider provider, YamlTree node, YamlPath path) {
			MapNode map = this.binding.map(node, path);
			if (map == null) {
				return;
			}
			Map<String, YamlTree> entries = this.binding.entries(map, path, MODELS::equals);
			YamlPath modelsPath = path.child(MODELS);
			if (!entries.containsKey(MODELS)) {
				problem(modelsPath, "'" + MODELS + "' 가 없다 — 모델이 없는 공급자는 빈 목록([])으로 적는다", map.location());
				return;
			}
			ListNode list = this.binding.list(entries.get(MODELS), modelsPath);
			if (list == null) {
				return;
			}
			Set<String> seen = new HashSet<>();
			for (int i = 0; i < list.items().size(); i++) {
				model(provider, list.items().get(i), modelsPath.index(i), seen);
			}
		}

		private void model(Provider provider, YamlTree node, YamlPath path, Set<String> seen) {
			MapNode map = this.binding.map(node, path);
			if (map == null) {
				return;
			}
			Map<String, YamlTree> entries = this.binding.entries(map, path, MODEL_KEYS::contains);
			Optional<String> id = id(entries, path, map.location(), seen);
			Optional<Integer> budget = budget(entries, path, map.location());
			Optional<Boolean> structured = structured(entries, path, map.location());
			if (id.isPresent() && budget.isPresent() && structured.isPresent()) {
				this.models.add(new LlmModel(provider, id.get(), budget.get(), structured.get()));
			}
		}

		private Optional<String> id(Map<String, YamlTree> entries, YamlPath path, TextLocation at, Set<String> seen) {
			YamlPath idPath = path.child(ID);
			if (!entries.containsKey(ID)) {
				problem(idPath, "'" + ID + "' 가 없다", at);
				return Optional.empty();
			}
			Located<String> id = this.binding.string(entries.get(ID), idPath);
			if (id == null) {
				return Optional.empty();
			}
			String value = id.value();
			if (value.isEmpty() || value.length() > MAX_ID_LENGTH) {
				problem(idPath, "모델 ID 는 1자 이상 " + MAX_ID_LENGTH + "자 이하여야 한다 (지금 " + value.length() + "자)",
						id.location());
				return Optional.empty();
			}
			if (!value.chars().allMatch(c -> c >= FIRST_ID_CHARACTER && c <= LAST_ID_CHARACTER)) {
				problem(idPath, "모델 ID 에는 공백 · 제어 문자 · ASCII 밖의 문자를 쓸 수 없다", id.location());
				return Optional.empty();
			}
			if (!seen.add(value)) {
				problem(idPath, "같은 공급자에 같은 모델 ID '" + value + "' 가 앞에 또 있다", id.location());
				return Optional.empty();
			}
			return Optional.of(value);
		}

		private Optional<Integer> budget(Map<String, YamlTree> entries, YamlPath path, TextLocation at) {
			YamlPath budgetPath = path.child(BUDGET);
			if (!entries.containsKey(BUDGET)) {
				problem(budgetPath, "'" + BUDGET + "' 가 없다", at);
				return Optional.empty();
			}
			Located<Integer> budget = this.binding.integer(entries.get(BUDGET), budgetPath);
			if (budget == null) {
				return Optional.empty();
			}
			if (budget.value() < MIN_BUDGET || budget.value() > MAX_BUDGET) {
				problem(budgetPath, "입력 토큰 예산은 " + MIN_BUDGET + " 이상 " + MAX_BUDGET + " 이하여야 한다 (지금 "
						+ budget.value() + ")", budget.location());
				return Optional.empty();
			}
			return Optional.of(budget.value());
		}

		private Optional<Boolean> structured(Map<String, YamlTree> entries, YamlPath path, TextLocation at) {
			YamlPath structuredPath = path.child(STRUCTURED);
			if (!entries.containsKey(STRUCTURED)) {
				problem(structuredPath, "'" + STRUCTURED + "' 가 없다", at);
				return Optional.empty();
			}
			Located<Boolean> structured = this.binding.bool(entries.get(STRUCTURED), structuredPath);
			return (structured != null) ? Optional.of(structured.value()) : Optional.empty();
		}

		private void problem(YamlPath path, String message, TextLocation location) {
			this.problems.add(new ModelCatalogProblem(path, message, location));
		}

		private static String providerKeys() {
			List<String> keys = new ArrayList<>();
			for (Provider provider : Provider.values()) {
				keys.add(provider.key());
			}
			return String.join(" · ", keys);
		}

	}

}
