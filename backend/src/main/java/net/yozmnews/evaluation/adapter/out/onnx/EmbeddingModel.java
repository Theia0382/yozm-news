package net.yozmnews.evaluation.adapter.out.onnx;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Properties;

/**
 * 기본 로컬 임베딩 모델의 정보 — classpath {@code embedding-model/} 의 {@code model.properties}(원본 {@code backend/embedding-model.properties} —
 * 정본)와 모델 · 토크나이저 파일 · docs/specs/evaluation.md §4 · ADR-0091.
 * <p>
 * 파일은 빌드가 고정 리비전 · 해시로 받아 넣는다. 읽을 때 sha256 을 다시 확인한다 — 같은 모델 버전 문자열에 다른 파일이 오지 않게 한다.
 *
 * @param modelVersion 모델 버전 — 임베더 정보로 밝히는 값
 * @param dimensions 벡터 차원
 * @param inputPrefix 텍스트마다 앞에 붙일 문자열
 * @param modelFile ONNX 모델 파일
 * @param tokenizerFile HF tokenizer.json
 */
record EmbeddingModel(String modelVersion, int dimensions, String inputPrefix, Path modelFile, Path tokenizerFile) {

	/** classpath 의 자리 */
	static final String DIRECTORY = "embedding-model/";

	EmbeddingModel {
		Objects.requireNonNull(modelVersion, "modelVersion");
		Objects.requireNonNull(inputPrefix, "inputPrefix");
		Objects.requireNonNull(modelFile, "modelFile");
		Objects.requireNonNull(tokenizerFile, "tokenizerFile");
	}

	/**
	 * classpath 에서 읽는다.
	 * @throws IllegalStateException 파일이 없거나 파일 시스템의 파일이 아니거나(jar 안) 해시가 다를 때
	 */
	static EmbeddingModel load() {
		return load(EmbeddingModel.class.getClassLoader());
	}

	/** 시험이 다른 classpath 를 넘긴다 */
	static EmbeddingModel load(ClassLoader loader) {
		Properties properties = new Properties();
		try (InputStream input = loader.getResourceAsStream(DIRECTORY + "model.properties")) {
			if (input == null) {
				throw new IllegalStateException("임베딩 모델 정보(" + DIRECTORY + "model.properties)가 classpath 에 없다");
			}
			properties.load(input);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		Path model = file(loader, "model.onnx");
		Path tokenizer = file(loader, "tokenizer.json");
		verify(model, required(properties, "model.sha256"));
		verify(tokenizer, required(properties, "tokenizer.sha256"));
		int dimensions;
		try {
			dimensions = Integer.parseInt(required(properties, "dimensions"));
		}
		catch (NumberFormatException ex) {
			throw new IllegalStateException("임베딩 모델 정보의 dimensions 가 정수가 아니다", ex);
		}
		return new EmbeddingModel(required(properties, "model-version"), dimensions,
				required(properties, "input-prefix"), model, tokenizer);
	}

	private static String required(Properties properties, String key) {
		String value = properties.getProperty(key);
		if (value == null) {
			throw new IllegalStateException("임베딩 모델 정보에 " + key + " 가 없다");
		}
		return value;
	}

	private static Path file(ClassLoader loader, String name) {
		URL url = loader.getResource(DIRECTORY + name);
		if (url == null) {
			throw new IllegalStateException("임베딩 모델 파일(" + DIRECTORY + name + ")이 classpath 에 없다");
		}
		if (!"file".equals(url.getProtocol())) {
			throw new IllegalStateException(
					"임베딩 모델 파일(" + DIRECTORY + name + ")이 파일 시스템에 없다 — " + url.getProtocol() + " 로 실행했다");
		}
		try {
			return Path.of(url.toURI());
		}
		catch (URISyntaxException ex) {
			throw new IllegalStateException("임베딩 모델 파일의 경로를 읽지 못했다 — " + name, ex);
		}
	}

	private static void verify(Path file, String expected) {
		String actual = sha256(file);
		if (!actual.equals(expected)) {
			throw new IllegalStateException(
					"임베딩 모델 파일의 sha256 이 다르다 — " + file.getFileName() + ": " + actual + " (기대 " + expected + ")");
		}
	}

	private static String sha256(Path file) {
		try (InputStream input = Files.newInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[1 << 16];
			for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
				digest.update(buffer, 0, read);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 이 없다", ex);
		}
	}

}
