package net.yozmnews;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

/** 시험 도우미 — Gradle 이 넘긴 시스템 속성의 파일 경로 */
final class GradleProperties {

	private GradleProperties() {
	}

	/** 시스템 속성 값을 경로로. 없거나 비어 있으면 Gradle 밖에서 실행한 것이다. */
	static Path pathFrom(String property, @Nullable String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("시스템 속성 " + property + " 이 없다 — 테스트는 Gradle 로 실행한다");
		}
		return Path.of(value);
	}

}
