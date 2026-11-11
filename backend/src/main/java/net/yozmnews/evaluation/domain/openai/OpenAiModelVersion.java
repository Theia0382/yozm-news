package net.yozmnews.evaluation.domain.openai;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 외부 임베딩의 모델 버전 규칙의 정본 — docs/specs/evaluation.md §4 {@code domain/openai} · ADR-0090.
 * <p>
 * {@code openai-compatible:{모델}:{차원}:} + SHA-256({@code {끝의 / 를 뗀 주소}\n{입력 앞 문자열}\n{차원 요청 여부}}) 앞 12 hex. 같은 모델
 * 이름이라도 주소 · 입력 앞 문자열 · 차원 · 차원 요청 여부가 다르면 다른 버전이다.
 */
public final class OpenAiModelVersion {

	/** 버전의 앞부분 */
	public static final String PREFIX = "openai-compatible:";

	private OpenAiModelVersion() {
	}

	public static String of(String model, int dimensions, URI baseUrl, String inputPrefix, boolean sendDimensions) {
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(baseUrl, "baseUrl");
		Objects.requireNonNull(inputPrefix, "inputPrefix");
		String base = baseUrl.toString();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		String material = base + "\n" + inputPrefix + "\n" + sendDimensions;
		return PREFIX + model + ":" + dimensions + ":" + sha256(material).substring(0, 12);
	}

	private static String sha256(String text) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 이 없다", ex);
		}
	}

}
