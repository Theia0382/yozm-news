package net.yozmnews;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import net.yozmnews.CodeFences.Block;
import org.jspecify.annotations.Nullable;

/**
 * 예시를 시험이 지키는 운영 안내서 — {@code docs/operations/sources.md} · {@code docs/operations/tag-sets.md}. 파일 경로는
 * Gradle 이 안내서마다 시스템 속성으로 넘긴다({@code backend/build.gradle.kts}).
 */
public enum OperationsGuide {

	/** 소스 등록 — 예시 프로필 */
	SOURCES("yozmnews.sources-guide-file"),

	/** 태그 체계 바꾸기 — 예시 원문 */
	TAG_SETS("yozmnews.tag-sets-guide-file");

	/** 안내서에 쓸 수 있는 코드 블록 — 예시를 다른 표지로 쓰면 시험을 빠져나가므로 이 밖의 블록을 두지 않는다 */
	public static final Set<String> CODE_BLOCK_INFO = Set.of("yaml", "sh");

	private final String pathProperty;

	OperationsGuide(String pathProperty) {
		this.pathProperty = pathProperty;
	}

	/** 안내서 원문. 파일이 없거나 읽지 못하면 실패한다. */
	public String text() {
		Path path = pathFrom(this.pathProperty, System.getProperty(this.pathProperty));
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

	/** 안내서의 코드 블록 — 문서 순서 */
	public List<Block> blocks() {
		return CodeFences.extract(text());
	}

	/** 안내서의 {@code yaml} 블록 — 문서 순서 */
	public List<Block> yamlBlocks() {
		return blocks().stream().filter((block) -> block.info().equals("yaml")).toList();
	}

	/** {@link #CODE_BLOCK_INFO} 밖의 코드 블록 — "{줄}번째 줄: '{표지}'" */
	public List<String> blocksOutsideTheAllowedInfo() {
		return blocks().stream()
			.filter((block) -> !CODE_BLOCK_INFO.contains(block.info()))
			.map((block) -> block.line() + "번째 줄: '" + block.info() + "'")
			.toList();
	}

	/** 시스템 속성 값을 경로로. 없거나 비어 있으면 Gradle 밖에서 실행한 것이다. */
	static Path pathFrom(String property, @Nullable String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("시스템 속성 " + property + " 이 없다 — 테스트는 Gradle 로 실행한다");
		}
		return Path.of(value);
	}

}
