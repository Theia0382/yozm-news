package net.yozmnews.source.domain.profile;

import java.util.Objects;

import net.yozmnews.source.domain.profile.text.TextLocation;

/**
 * 프로필의 문제 하나 — 운영자에게 그대로 보여 줄 값이다.
 *
 * @param message 운영자가 읽을 문구
 * @param location 텍스트 안의 줄:열 — 알 수 없으면 {@link TextLocation#UNKNOWN}
 */
public record ProfileProblem(ProfilePath path, ProblemCode code, String message, TextLocation location) {

	public ProfileProblem {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(location, "location");
	}

	@Override
	public String toString() {
		return this.location + " " + (this.path.segments().isEmpty() ? "(최상위)" : this.path) + ": " + this.message
				+ " [" + this.code + "]";
	}

}
