package net.yozmnews.event.adapter.out.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 기본 로컬 임베딩 모델의 정보 읽기 — 빌드가 넣은 classpath 의 정보 · 파일, 해시가 다른 파일 · 정보가 없는 classpath · jar 안의 파일을 본다.
 * <p>
 * 지키는 규칙: ADR-0091(같은 모델 버전 문자열에 다른 파일이 오지 않는다 · 접두사의 끝 공백)
 */
class EmbeddingModelTests {

	@Test
	void readsTheModelInformationFromTheClasspath() {
		EmbeddingModel model = EmbeddingModel.load();

		assertThat(model.modelVersion()).isEqualTo("intfloat/multilingual-e5-small@614241f6:qint8");
		assertThat(model.dimensions()).isEqualTo(384);
		assertThat(model.inputPrefix()).isEqualTo("query: ").hasSize(7);
		assertThat(model.modelFile()).isRegularFile().hasFileName("model.onnx");
		assertThat(model.tokenizerFile()).isRegularFile().hasFileName("tokenizer.json");
	}

	@Test
	void refusesAFileWhoseHashDiffers(@TempDir Path root) throws IOException {
		Path directory = Files.createDirectories(root.resolve("embedding-model"));
		Files.writeString(directory.resolve("model.onnx"), "다른 모델");
		Files.writeString(directory.resolve("tokenizer.json"), "{}");
		Files.writeString(directory.resolve("model.properties"), """
				model.sha256=0000000000000000000000000000000000000000000000000000000000000000
				tokenizer.sha256=0000000000000000000000000000000000000000000000000000000000000000
				model-version=test@0
				dimensions=4
				input-prefix=
				""");

		try (URLClassLoader loader = new URLClassLoader(new URL[] { root.toUri().toURL() }, null)) {
			assertThatIllegalStateException().isThrownBy(() -> EmbeddingModel.load(loader))
				.withMessageContaining("sha256")
				.withMessageContaining("model.onnx");
		}
	}

	@Test
	void refusesModelFilesInsideAJar(@TempDir Path root) throws IOException {
		Path jar = root.resolve("model.jar");
		try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
			for (String name : new String[] { "model.properties", "model.onnx", "tokenizer.json" }) {
				output.putNextEntry(new JarEntry("embedding-model/" + name));
				output.write("x".getBytes(StandardCharsets.UTF_8));
				output.closeEntry();
			}
		}

		try (URLClassLoader loader = new URLClassLoader(new URL[] { jar.toUri().toURL() }, null)) {
			assertThatIllegalStateException().isThrownBy(() -> EmbeddingModel.load(loader))
				.withMessageContaining("파일 시스템에 없다")
				.withMessageContaining("jar");
		}
	}

	@Test
	void refusesAClasspathWithoutTheModelInformation(@TempDir Path root) throws IOException {
		try (URLClassLoader loader = new URLClassLoader(new URL[] { root.toUri().toURL() }, null)) {
			assertThatIllegalStateException().isThrownBy(() -> EmbeddingModel.load(loader))
				.withMessageContaining("model.properties");
		}
	}

}
