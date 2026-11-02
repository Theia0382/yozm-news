package net.yozmnews.source.domain.profile;

import java.util.Objects;

import net.yozmnews.platform.TextLocation;
import net.yozmnews.platform.YamlPath;
import net.yozmnews.platform.YamlProblem;

/**
 * 프로필의 문제 하나 — 운영자에게 그대로 보여 줄 값이다.
 *
 * @param message 운영자가 읽을 문구
 * @param location 텍스트 안의 줄:열 — 알 수 없으면 {@link TextLocation#UNKNOWN}
 */
public record ProfileProblem(YamlPath path, ProblemCode code, String message, TextLocation location) {

	public ProfileProblem {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(location, "location");
	}

	/** 공통 YAML 문제를 같은 경로 · 문구 · 위치의 프로필 문제로 옮긴다 — 종류는 {@link ProblemCode#of} 가 옮긴다 */
	static ProfileProblem of(YamlProblem problem) {
		return new ProfileProblem(problem.path(), ProblemCode.of(problem.kind()), problem.message(), problem.location());
	}

	@Override
	public String toString() {
		return this.location + " " + (this.path.segments().isEmpty() ? "(최상위)" : this.path) + ": " + this.message
				+ " [" + this.code + "]";
	}

}
