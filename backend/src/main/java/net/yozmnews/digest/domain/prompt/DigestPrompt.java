package net.yozmnews.digest.domain.prompt;

import java.util.Objects;

/**
 * 생성 요청의 프롬프트 — 지시 구획 · 사용자 구획 · 출력 JSON Schema 문서 — docs/specs/digest.md §5 · digest-R23
 * <p>
 * 출력 스키마는 언제나 싣는다 — 모델이 구조화 출력을 지원하면 관문이 강제 인자로 보내고, 지원하지 않으면 지시에도 같은 문서가 들어 있다.
 *
 * @param instructions 지시 구획 — 역할 · 규칙 · 길이 기준
 * @param userContent 사용자 구획 — 프로필 · 묶음 · 후보의 JSON 문서 하나. 신뢰할 수 없는 글은 모두 여기에만 있다
 * @param outputSchema 출력 JSON Schema 문서({@code DigestOutputSchema.json()})
 */
public record DigestPrompt(String instructions, String userContent, String outputSchema) {

	/**
	 * @throws IllegalArgumentException 지시 · 사용자 구획 · 스키마가 공백뿐일 때
	 */
	public DigestPrompt {
		Objects.requireNonNull(instructions, "instructions");
		Objects.requireNonNull(userContent, "userContent");
		Objects.requireNonNull(outputSchema, "outputSchema");
		if (instructions.isBlank() || userContent.isBlank() || outputSchema.isBlank()) {
			throw new IllegalArgumentException("프롬프트 구획이 비었다");
		}
	}

}
