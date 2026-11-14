package net.yozmnews.llm.domain.catalog;

import java.util.Objects;

import net.yozmnews.platform.TextLocation;
import net.yozmnews.platform.YamlPath;

/**
 * 허용 모델 목록 파일의 문제 하나 — docs/specs/llm.md §4 · llm-R2.
 *
 * @param location 원문 안의 줄:열 — 알 수 없으면 {@link TextLocation#UNKNOWN}
 */
public record ModelCatalogProblem(YamlPath path, String message, TextLocation location) {

	public ModelCatalogProblem {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(location, "location");
	}

	@Override
	public String toString() {
		return this.location + " " + (this.path.segments().isEmpty() ? "(최상위)" : this.path) + ": " + this.message;
	}

}
