package net.yozmnews.llm;

import java.util.Objects;
import java.util.Optional;

/**
 * LLM 공급자 — docs/specs/llm.md §3 · llm-R1.
 * <p>
 * 키는 허용 모델 목록 파일과 저장 값에 쓰는 이름이다 — 바꾸지 않는다.
 */
public enum Provider {

	/** OpenAI API */
	OPENAI("openai"),
	/** Anthropic API */
	ANTHROPIC("anthropic");

	private final String key;

	Provider(String key) {
		this.key = key;
	}

	public String key() {
		return this.key;
	}

	/** 모르는 키면 빈 값 — 대소문자를 가린다 */
	public static Optional<Provider> ofKey(String key) {
		Objects.requireNonNull(key, "key");
		for (Provider provider : values()) {
			if (provider.key.equals(key)) {
				return Optional.of(provider);
			}
		}
		return Optional.empty();
	}

}
