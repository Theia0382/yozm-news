package net.yozmnews.platform;

import java.util.Objects;

/**
 * YAML 공통 문제 하나 — docs/specs/platform.md §3 · platform-R17.
 *
 * @param message 사람이 읽을 문구 — 문서 종류를 모른다
 * @param location 원문 안의 줄:열 — 알 수 없으면 {@link TextLocation#UNKNOWN}
 */
public record YamlProblem(YamlPath path, YamlProblemKind kind, String message, TextLocation location) {

	public YamlProblem {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(location, "location");
	}

	@Override
	public String toString() {
		return this.location + " " + (this.path.segments().isEmpty() ? "(최상위)" : this.path) + ": " + this.message
				+ " [" + this.kind + "]";
	}

}
