package net.yozmnews;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 개발용 Compose 파일(저장소 루트 {@code compose.yaml})에서 서비스 이미지를 읽는다. PostgreSQL 버전의 정본이 그
 * 파일이라 테스트도 같은 이미지를 쓴다. 파일 경로는 Gradle 이 시스템 속성으로 넘긴다({@code backend/build.gradle.kts}).
 */
final class DevComposeFile {

	static final String PATH_PROPERTY = "yozmnews.dev-compose-file";

	private DevComposeFile() {
	}

	/** {@code compose.yaml} 의 {@code postgres} 서비스 이미지. */
	static String postgresImage() {
		return imageOf(pathFrom(System.getProperty(PATH_PROPERTY)), "postgres");
	}

	/** 시스템 속성 값을 경로로. 없거나 비어 있으면 Gradle 밖에서 실행한 것이다. */
	static Path pathFrom(@Nullable String propertyValue) {
		if (propertyValue == null || propertyValue.isBlank()) {
			throw new IllegalStateException("시스템 속성 " + PATH_PROPERTY + " 이 없다 — 테스트는 Gradle 로 실행한다");
		}
		return Path.of(propertyValue);
	}

	/**
	 * {@code file} 의 {@code services.<service>.image}. 파일 · 서비스 · image 가 없거나 image 가 보간식이면 실패한다 —
	 * 다른 이미지로 조용히 넘어가지 않는다.
	 */
	static String imageOf(Path file, String service) {
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("개발용 Compose 파일이 없다: " + file);
		}
		Object root;
		try (Reader reader = Files.newBufferedReader(file)) {
			root = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
		}
		catch (IOException ex) {
			throw new IllegalStateException("개발용 Compose 파일을 읽지 못했다: " + file, ex);
		}
		Object services = (root instanceof Map<?, ?> rootMap) ? rootMap.get("services") : null;
		Object definition = (services instanceof Map<?, ?> servicesMap) ? servicesMap.get(service) : null;
		if (!(definition instanceof Map<?, ?> serviceMap)) {
			throw new IllegalStateException(file + " 에 서비스 '" + service + "' 가 없다");
		}
		if (!(serviceMap.get("image") instanceof String image) || image.isBlank()) {
			throw new IllegalStateException(file + " 의 서비스 '" + service + "' 에 image 가 없다");
		}
		// Compose 는 ${VAR} 와 $VAR 를 모두 보간한다. 이미지 참조에는 $ 가 올 수 없다
		if (image.contains("$")) {
			throw new IllegalStateException(file + " 의 서비스 '" + service + "' image 가 보간식이라 읽을 수 없다: " + image);
		}
		return image;
	}

}
