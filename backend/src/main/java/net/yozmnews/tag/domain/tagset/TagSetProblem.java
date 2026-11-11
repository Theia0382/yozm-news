package net.yozmnews.tag.domain.tagset;

import java.util.Objects;

import net.yozmnews.platform.TextLocation;
import net.yozmnews.platform.YamlPath;
import net.yozmnews.platform.YamlProblem;

/**
 * 태그 체계 파일의 문제 하나 — docs/specs/tag.md §4 · tag-R5.
 *
 * @param message 사람이 읽을 문구
 * @param location 원문 안의 줄:열 — 알 수 없으면 {@link TextLocation#UNKNOWN}
 */
public record TagSetProblem(YamlPath path, TagSetProblemCode code, String message, TextLocation location) {

	public TagSetProblem {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(location, "location");
	}

	/** 공통 YAML 문제를 같은 경로 · 문구 · 위치의 체계 문제로 옮긴다 — 종류는 {@link TagSetProblemCode#of} 가 옮긴다 */
	static TagSetProblem of(YamlProblem problem) {
		return new TagSetProblem(problem.path(), TagSetProblemCode.of(problem.kind()), problem.message(),
				problem.location());
	}

	@Override
	public String toString() {
		return this.location + " " + (this.path.segments().isEmpty() ? "(최상위)" : this.path) + ": " + this.message
				+ " [" + this.code + "]";
	}

}
