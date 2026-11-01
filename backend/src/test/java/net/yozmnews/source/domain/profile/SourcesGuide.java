package net.yozmnews.source.domain.profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

/**
 * 운영 안내서 {@code docs/operations/sources.md} 의 원문. 안내서의 예시 프로필이 지금의 검증기를 통과하는지 테스트가 본다.
 * 파일 경로는 Gradle 이 시스템 속성으로 넘긴다({@code backend/build.gradle.kts}).
 */
final class SourcesGuide {

	static final String PATH_PROPERTY = "yozmnews.sources-guide-file";

	private SourcesGuide() {
	}

	/** 안내서 원문. 파일이 없거나 읽지 못하면 실패한다. */
	static String text() {
		Path path = pathFrom(System.getProperty(PATH_PROPERTY));
		if (!Files.isRegularFile(path)) {
			throw new IllegalStateException("운영 안내서가 없다: " + path);
		}
		try {
			return Files.readString(path);
		}
		catch (IOException ex) {
			throw new IllegalStateException("운영 안내서를 읽지 못했다: " + path, ex);
		}
	}

	/** 시스템 속성 값을 경로로. 없거나 비어 있으면 Gradle 밖에서 실행한 것이다. */
	static Path pathFrom(@Nullable String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("시스템 속성 " + PATH_PROPERTY + " 이 없다 — 테스트는 Gradle 로 실행한다");
		}
		return Path.of(value);
	}

}
