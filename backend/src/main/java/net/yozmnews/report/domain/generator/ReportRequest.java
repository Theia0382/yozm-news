package net.yozmnews.report.domain.generator;

import java.util.Objects;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.domain.alias.ReportAliases;
import net.yozmnews.report.domain.prompt.ReportPrompt;

/**
 * 생성 요청 · docs/specs/report.md §4 · report-R2
 *
 * @param userId 리포트의 사용자 — 그 사용자의 저장한 키로 부른다
 * @param model 쓸 모델
 * @param effort 추론 강도 — 사용자 설정
 * @param aliases 맞춘 입력에 매긴 갈래 · 인용 대상의 별칭 — 갈래가 하나 이상이다
 * @param prompt 이번 호출의 프롬프트 — 다시 묻는 호출은 다시 묻는 프롬프트
 */
public record ReportRequest(UserId userId, LlmModel model, ReasoningEffort effort, ReportAliases aliases,
		ReportPrompt prompt) {

	/**
	 * @throws IllegalArgumentException 갈래가 하나도 없을 때 — 갈래 0 개는 생성기를 부르지 않는다
	 */
	public ReportRequest {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(effort, "effort");
		Objects.requireNonNull(aliases, "aliases");
		Objects.requireNonNull(prompt, "prompt");
		if (aliases.hasNoSections()) {
			throw new IllegalArgumentException("갈래가 없는 요청이다");
		}
	}

}
