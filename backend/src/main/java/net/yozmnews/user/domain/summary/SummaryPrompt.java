package net.yozmnews.user.domain.summary;

import java.util.List;
import java.util.Objects;

/**
 * 정리 호출 하나의 프롬프트 — 지시 · 사용자 구획 · 출력 스키마 문서와, 출력을 읽을 때 쓸 꼭대기 별칭. docs/specs/user.md §4
 *
 * @param instructions 지시
 * @param userContent 사용자 구획(JSON 문서)
 * @param outputSchema 출력 JSON Schema 문서
 * @param aliases 꼭대기 별칭 — 재료의 꼭대기 ID 순과 같은 순서
 */
public record SummaryPrompt(String instructions, String userContent, String outputSchema, List<String> aliases) {

	public SummaryPrompt {
		Objects.requireNonNull(instructions, "instructions");
		Objects.requireNonNull(userContent, "userContent");
		Objects.requireNonNull(outputSchema, "outputSchema");
		aliases = List.copyOf(aliases);
	}

}
